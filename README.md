# JDK-8059010

Repro for an openjdk bug that was closed with a could not repro.

```
$ export LD_LIBRARY_PATH=target/native/linux/x86_64/
$ java -jar target/JDK-8059010-0.1.0-SNAPSHOT-standalone.jar
```

Now wait for an unspecified amount of time.  The process will error
out with the expected error.


## License


Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
