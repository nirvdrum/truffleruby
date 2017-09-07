# Using TruffleRuby with JDK 9 EA

TruffleRuby is designed to be run with a JVM that has the Graal compiler. The
easiest way to do this is to use the GraalVM, which includes a JDK, the Graal
compiler, and TruffleRuby, all in one package.

However, as a standard Java application, a build of TruffleRuby from source is
also immediately compatible with an external JDK 9 early-access build, which you
can download from https://jdk9.java.net/download/:

```
$ JAVACMD=.../jdk-9.jdk/bin/java \
    bin/ruby -e "puts 'hello'"
[ruby] PERFORMANCE this JVM does not have the Graal compiler - performance will be limited - see doc/user/using-graalvm.md
hello
```

This configuration does not have the Graal compiler available and will be slow.
To use the Graal compiler with an external JDK 9 EA we need to build Truffle and
Graal using JDK 9 EA. Follow the instructions for contributors building Graal in
[Building Graal](../contributor/building-graal.md), setting `JAVA_HOME` to be
JDK 9 EA. Then we need some additional flags:

```
$ JAVACMD=.../jdk-9.jdk/bin/java \
    bin/ruby \
    -J-XX:+UnlockExperimentalVMOptions \
    -J-XX:+EnableJVMCI \
    -J--add-exports=java.base/jdk.internal.module=com.oracle.graal.graal_core \
    -J--module-path=.../truffle/mxbuild/modules/com.oracle.truffle.truffle_api.jar:.../compiler/mxbuild/modules/com.oracle.graal.graal_core.jar \
    --no-bootclasspath -e 'p Truffle.graal?'
true
```
