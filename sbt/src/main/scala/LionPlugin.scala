import com.github.fommil.lion.alloc.{AllocationSizes, AllocationReporter, AllocationAnalyser, AllocationParser}
import java.io.FileNotFoundException
import sbt._
import sbt.ForkOptions
import sbt.Keys._
import sbt.Attributed.data

import com.github.fommil
import fommil.lion.gc.{GcReporter, GcParser}
import fommil.utils.{StringResourceSupport, StringFileSupport, StringGzResourceSupport}
import fommil.utils.Pimps._

object LionPlugin extends Plugin with StringGzResourceSupport with StringResourceSupport with StringFileSupport {

  val lion = TaskKey[Unit]("lion", "Run a main class with lions-share profiling.")
  val lionRuns = SettingKey[Int]("number of times to run the main class (without instrumentation).")
  val lionClass = SettingKey[Option[String]]("main class to run during lions-share profiling.")
  val lionOut = SettingKey[File]("output directory for lions-share reports and log files.")
  val lionAllocRuns = SettingKey[Int]("enable additional runs with the allocation agent (slow).")
  val lionAllocTrim = SettingKey[Option[Int]]("only plot this many of the top-allocated objects for each datum.")
  val lionAllocRate = SettingKey[Int]("number of seconds to wait between polling the allocation agent.")
  val lionAllocTrace = SettingKey[Map[String, Long]]("classes and byte sample threshold")

  // https://github.com/sbt/sbt/issues/1260
  private val agent = "com.github.fommil.lion" % "agent" % "1.0-SNAPSHOT" classifier("assembly") intransitive()

  override val projectSettings = Seq(
    libraryDependencies += agent,
    lionRuns := 10,
    lionClass := None,
    lionOut := new File("lion-results"),
    lionAllocRuns := 1,
    lionAllocTrim := Some(10),
    lionAllocRate := 5,
    // defaults are to sample core objects every 10MB
    lionAllocTrace := List(
      "java/lang/Object",
      "java/lang/Boolean",
      "java/lang/Long",
      "java/lang/Integer",
      "java/lang/Double",
      "java/lang/String",
      "boolean",
      "long",
      "int",
      "double",
      "char",
      "byte",
      "scala/Some"
    ).map(c => (c, 1048576L)).toMap,
    lion := runLion(
      (fullClasspath in Runtime).value,
      (lionClass in Runtime).value orElse
        (mainClass in Runtime).value orElse
        (discoveredMainClasses in Runtime).value.headOption,
      (streams in Runtime).value,
      (update in Runtime).value,
      (lionRuns in lion).value,
      (lionAllocRuns in lion).value,
      (lionAllocTrim in lion).value,
      (lionAllocRate in lion).value,
      (lionAllocTrace in lion).value,
      (lionOut in lion).value,
      (javaOptions in Runtime).value
    )
  )

  def agentJar(update: UpdateReport): File = {
   for {
     report <- update.configuration("runtime-internal").get.modules
     module = report.module
     if module.organization == agent.organization
     if module.name == agent.name
     if module.revision == agent.revision
     artifacts <- report.artifacts
     file = artifacts._2
   } yield file
 }.head

  def runLion(cp: Classpath,
              main: Option[String],
              streams: TaskStreams,
              update: UpdateReport,
              runs: Int,
              allocRuns: Int,
              allocTrim: Option[Int],
              sampleSeconds: Int,
              trace: Map[String, Long],
              out: File,
              vmArgs: Seq[String]): Unit = {
    val log = streams.log
    if (main.isEmpty) {
      log.warn("lionClass (or mainClass) must be set")
      return
    }

    log.info(s"running the lions-share $runs times for ${main.get}")
    out.mkdirs()

    def fork(opts: String*) = {
      val javaOptions = opts ++ vmArgs
      val runner = new ForkRun(ForkOptions(runJVMOptions = javaOptions))
      toError(runner.run(main.get, data(cp), Nil, log))
    }

    // Non-instrumented runs with GC logging
    (1 to runs).map { run =>
      new File(out, s"gc-$run.log") withEffect { gcLog =>
        fork(
          s"-Xloggc:${gcLog.getAbsolutePath}",
          "-XX:+PrintGCDetails", "-XX:+PrintGCDateStamps",
          "-XX:+PrintTenuringDistribution", "-XX:+PrintHeapAtGC"
        )
      }
    }.map {gcLog =>
      GcParser.parse(fromFile(gcLog))
    }.withEffect { processes =>
      if (processes.nonEmpty) {
        GcReporter.gcReport(processes, new File(out, "gc.js"))
        val report = new File(out, "gc.html")
        toFile(report, fromRes("/com/github/fommil/lion/gc/report.html"))
        log.info(s"lions-share garbage collection report is available at ${report.getAbsolutePath }")
      }
    }

    // Instrumented runs with Allocation Agent
    val jar = agentJar(update)
    val traces = (for((c,s) <- trace) yield s"$c:$s").mkString(",")
    (1 to allocRuns).map { run =>
      new File(out, s"alloc-$run.log") withEffect { allocLog =>
        fork(s"-javaagent:$jar=$allocLog $sampleSeconds $traces")
      }
    }.map { allocLog =>
      AllocationParser.parse(fromFile(allocLog)) collect {
        case a: AllocationSizes if allocTrim.isDefined => a.trim(allocTrim.get)
        case a => a
      }
    } withEffect { processes =>
      if (processes.nonEmpty) {
        AllocationReporter.allocReport(processes, new File(out, "alloc.js"))
        val report = new File(out, "alloc.html")
        toFile(report, fromRes("/com/github/fommil/lion/alloc/report.html"))
        log.info(s"lions-share allocation report is available at ${report.getAbsolutePath }")
      }
    }
    
    val report = new File(out, "index.html")
    toFile(report, fromRes("/lion-report.html"))
    log.info(s"lions-share summary report is available at ${report.getAbsolutePath}")
  }

}
