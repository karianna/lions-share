lions-share
===========

JVM development tools for finding memory and garbage problems.

It can be used as a library - as part of custom regression and performance testing frameworks -
or as an sbt plugin to get a report on a `main` class (or `App`).

Lion's Share produces HTML reports with interactive Google Charts.

## SBT Plugin

### Install

To use the plugin, add the following to your project's `project/plugins.sbt` file:

```scala
resolvers ++= Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.github.fommil.lion" % "sbt" % "1.0-SNAPSHOT")
```

Then you may run `sbt lion` to get a report using the default settings on your
project's `mainClass`.

### Customise

The full list of settings is best read [from the source code of the plugin](https://github.com/fommil/lions-share/blob/master/sbt/src/main/scala/LionPlugin.scala#L15).

The following are some example settings that can be defined in `build.sbt` to customise the setup:

```scala
lionRuns := 50 // number of times to run the main class (without instrumentation)

lionClass := path.to.MyMain // keep your mainclass but use this to run during lion

lionOut := new File("my-results") // output directory for lions-share reports and log files

lionAllocRuns := 0 // runs with the allocation agent (slow)

lionAllocTrim := None // only plot this many of the top-allocated objects for each datum. None doesn't do any trimming.

lionAllocRate := 30 // number of seconds to wait between polling the allocation agent.

lionAllocTrace := Map.empty[String, Long]] // classes and byte sample threshold
```

### Examples

There are some examples under the examples module, including:

* demonstration of `Some` creation churn and performance impact.
* shapeless' [`staging.scala`](http://github.com/milessabin/shapeless/blob/master/examples/src/main/scala/shapeless/examples/staging.scala) example which does runtime invocation of `scalac`.


## Library

To use the Garbage Collection analyser as part of your own testing or analysis framework, you must start your applications with these flags:

```
-Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC
```

The `GcParser` and `GcAnalyser` classes are then at your disposal, producing a `DataTable` case class that can be marshalled (using Spray JSON) to the exact form required by Google Charts [`DataTable` class](https://developers.google.com/chart/interactive/docs/reference#DataTable). See `GcReporter` and the companion `report.html` for an example.

To use the allocation agent, you must obtain `agent-assembly.jar` and start your application with

```
-javaagent:agent-assembly.jar=OUTFILE PERIOD TRACES
```

where

* `OUTFILE` is the file to write the samples
* `PERIOD` is the number of seconds between snapshotting the sampler
* `TRACES` is a comma separated list of `CLASSNAME:BYTES` pairs (optional)


## Known Problems

Please check the issue tracker to see if we are already aware of an issue (including a workaround). The biggest issues are;

* [SBT doesn't download the agent automatically](https://github.com/fommil/lions-share/issues/8)
* [Java 8 is not supported by the allocation agent](https://github.com/fommil/lions-share/issues/7) due to upstream bugs.
* [Only specific JVMs are supported by the garbage collection log parser](https://github.com/fommil/lions-share/blob/master/analysis/src/main/scala/com/github/fommil/lion/gc/parser.scala#L24), and [G1 is not supported at all](https://github.com/fommil/lions-share/issues/13).

You might need to run your application under a supported JVM, e.g.:

```
JAVA_HOME=/opt/jdk1.7.0_51 sbt lion
```

## Planned Features

1. CPU Sampling
2. Scala REPL support (e.g. allocation sizes for a block)
