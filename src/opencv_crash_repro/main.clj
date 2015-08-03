(ns opencv-crash-repro.main
  (:require [clojure.java.io :as io]
            [mikera.image.core :as imagez]
            [thinktopic.image.core :as think-image]
            [mikera.vectorz.matrix-api]
            [clojure.core.matrix :as mat])

  (:import  [javax.imageio ImageIO]
            [java.io ByteArrayInputStream ByteArrayOutputStream]
            [java.net URL]
            [java.awt.image BufferedImage]
            [org.opencv.core Mat Size CvType Rect Scalar])
  (:gen-class))


(mat/set-current-implementation :vectorz)
(clojure.lang.RT/loadLibrary org.opencv.core.Core/NATIVE_LIBRARY_NAME)

(defn load-image-from-pipeline
  ^BufferedImage [cur-url]
  (imagez/load-image (io/resource cur-url)))



(defn image-data-to-sub-record
  [image-record img-data type-keyword]
  (let [cv-matrix (img-data 0)
        bbox (img-data 1)
        write-img (think-image/opencv-to-image cv-matrix)
        width (.width cv-matrix)
        height (.height cv-matrix)
        mat-type (.type cv-matrix)
        write-format (if (= CvType/CV_8UC4 mat-type)
                       "PNG"
                       "JPG")
        extension (.toLowerCase write-format)
        ;;bucket (config/get-config :ingester-image-bucket)
        ;;key (get-full-key (:sku image-record) (:index image-record) type-keyword extension)
        ]
    ;;(write-image-to-s3-bucket! bucket key write-img write-format)
    { :product-image/sku (:sku image-record)
     :product-image/index (:index image-record)
     :product-image/source-url (:url image-record)
     :product-image/type (name type-keyword)
     :product-image/width (float width)
     :product-image/height (float height)
     :product-image/bounding-box (prn-str (into [] bbox))
     :resource/type :resource.type/product-image }))


(defn mult-then-floor
  [num mult]
  (Math/floor (* num mult)))

(defn ensure-valid-bbox
  [bbox width height]
  (let [bbox-width (think-image/bounding-box-width bbox)
        bbox-height (think-image/bounding-box-height bbox)]
    (if (or (< bbox-width (* width 0.10))
            (< bbox-height (* height 0.10)))
      [0 0 width height]
      bbox)))
        

(defn find-content-bounding-box
  "Finding the content bounding box is a complex procedure.  We first
  find a rough cut using edge detect.  We then find a more precise cut
  using grabcut and the bounding box found through the rough cut.
  Finally we scale it back up to full size and go on with our lives"
  [^Mat original-image]
  (let [original-width (.width original-image)
        original-height (.height original-image)
        [^Mat scaled-image scaled-bbox] (think-image/scale-opencv-mat
                                         original-image
                                         [0 0 original-width original-height] 256)
        
        scaled-width (.width scaled-image)
        scaled-height (.height scaled-image)
        ratio (if (> original-width original-height)
                (/ original-width scaled-width)
                (/ original-height scaled-height))
        ;;magic numbers found through testing
        ^Mat edges (think-image/opencv-mat-edge-detect scaled-image :low-threshold 70 :ratio 3)
        scaled-bbox (ensure-valid-bbox
                     (flatten (think-image/bounding-box-from-single-channel-cv-matrix edges))
                     scaled-width
                     scaled-height)
        
        data-mask (think-image/opencv-matrix-grabcut scaled-image scaled-bbox)
        
        
        final-bbox (flatten (think-image/bounding-box-from-single-channel-cv-matrix data-mask))
        
        
        scaled-final-bbox (ensure-valid-bbox
                           (into [] (map #(mult-then-floor % ratio) final-bbox))
                           original-width
                           original-height)]

    (think-image/opencv-release-matrix scaled-image)
    (think-image/opencv-release-matrix edges)
    (think-image/opencv-release-matrix data-mask)
    scaled-final-bbox))



(defn process-image-record
  [image-record]
  (try
    (think-image/opencv-matrix-context
      (let [{:keys [sku index url]} image-record
            image (load-image-from-pipeline url)
            ^Mat original-mat (think-image/image-to-opencv image)
            original-bounding-box (find-content-bounding-box original-mat)
            large-img-data (think-image/create-nice-mat-and-bounding-box original-mat original-bounding-box)
            medium-img-data (think-image/scale-opencv-mat (large-img-data 0) (large-img-data 1) 256)
            small-img-data (think-image/scale-opencv-mat (medium-img-data 0) (medium-img-data 1) 128)
            feature-img-data (think-image/square-matrix (large-img-data 0) (large-img-data 1) -1 256)
            data-mask (think-image/opencv-matrix-grabcut (feature-img-data 0) (feature-img-data 1))
            masked-feature-matrix (think-image/opencv-matrix-and-mask-to-masked-matrix (feature-img-data 0) data-mask)
            [ccv color-sig] (think-image/get-matrix-ccv-and-color-signature masked-feature-matrix (feature-img-data 1))
            [scaled-data-mask _] (think-image/scale-opencv-mat data-mask [0 0 256 256] 64)
            feature-img-data [masked-feature-matrix (feature-img-data 1)]

            shape-mask (think-image/mask-matrix-to-int-buffer scaled-data-mask)
            retval [(image-data-to-sub-record image-record [original-mat original-bounding-box] :original)
                    (image-data-to-sub-record image-record large-img-data :large)
                    (image-data-to-sub-record image-record medium-img-data :medium)
                    (image-data-to-sub-record image-record small-img-data :small)
                    (merge (image-data-to-sub-record image-record feature-img-data :feature)
                           { :product-image/color-coherence-vector (prn-str ccv)
                            :product-image/color-signature (prn-str color-sig)
                            :product-image/shape-mask (prn-str (into [] shape-mask)) })]]
        retval))
    (catch Throwable e
      (println "Fatal error processing url:" (:url image-record))
      (throw e))))


(def image-resources
  ["480000_bk_xl.jpg"
   "480000_cu_xl.jpg"
   "480000_fr_xl.jpg"
   "480000_in_xl.jpg"
   "480000_ou_xl.jpg"])


(def infinite-image-resources
  (mapcat identity (repeat image-resources)))

(def infinite-image-segments
  (map (fn [img]
         { :sku 480000
          :index 0
          :url img })
       infinite-image-resources))


(defn -main
  [& args]
  (doall (pmap process-image-record infinite-image-segments))
  0)
