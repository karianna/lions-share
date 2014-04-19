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
  val lionRuns = SettingKey[Int]("number of times to run the main class during lions-share profiling.")
  val lionClass = SettingKey[Option[String]]("main class to run during lions-share profiling.")
  val lionOut = SettingKey[File]("output directory for lions-share reports and log files.")
  val lionAlloc = SettingKey[Boolean]("enable the allocation agent (slows down the run).")
  val lionAllocTrim = SettingKey[Option[Int]]("only plot this many of the top-allocated objects for each datum.")
  val lionAllocRate = SettingKey[Int]("number of seconds to wait between polling the allocation agent.")
  val lionAllocTrace = SettingKey[Map[String, Long]](
    "classnames (using slash notation) to sample every given number of bytes"
  )

  // https://github.com/sbt/sbt/issues/1260
  //private val agent = "com.github.fommil.lion" % "agent" % "1.0-SNAPSHOT"
  private val agentFile = new File("agent-assembly.jar")
  if (!agentFile.isFile)
    throw new FileNotFoundException(
      "fix http://stackoverflow.com/questions/23090044 or manually install " + agentFile.getAbsoluteFile +
        " from ~/.ivy2/local/com.github.fommil.lion/agent/1.0-SNAPSHOT/jars/agent-assembly.jar"
    )

  override val projectSettings = Seq(
//    libraryDependencies += agent,
    lionRuns := 10,
    lionClass := None,
    lionOut := new File("lion-results"),
    lionAlloc := true,
    lionAllocTrim := Some(10),
    lionAllocRate := 5,
    // defaults are to sample core objects every 10MB
    lionAllocTrace := List(
      "java/lang/Object",
      "java/lang/Boolean",
      "java/lang/Long",
      "java/lang/Integer",
      "java/lang/Double",
      "boolean",
      "long",
      "int",
      "double",
      "char",
      "byte"
    ).map(c => (c, 1048576L)).toMap,
    lion := runLion(
      (fullClasspath in Runtime).value,
      (lionClass in Runtime).value orElse
        (mainClass in Runtime).value orElse
        (discoveredMainClasses in Runtime).value.headOption,
      (streams in Runtime).value,
      (update in Runtime).value,
      (lionRuns in lion).value,
      (lionAlloc in lion).value,
      (lionAllocTrim in lion).value,
      (lionAllocRate in lion).value,
      (lionAllocTrace in lion).value,
      (lionOut in lion).value,
      (javaOptions in Runtime).value
    )
  )

  def agentJar(update: UpdateReport): File = agentFile
//  {
//    for {
//      report <- update.configuration("runtime-internal").get.modules
//      module = report.module
//      if module.organization == agent.organization
//      if module.name == agent.name
//      if module.revision == agent.revision
//      artifacts <- report.artifacts
//      file = artifacts._2
//    } yield file
//  }.head

  def runLion(cp: Classpath,
              main: Option[String],
              streams: TaskStreams,
              update: UpdateReport,
              runs: Int,
              doAlloc: Boolean,
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

    val jar = agentJar(update)
    val logs = (1 to runs) map { run =>
      val gcLog = new File(out, s"gc-$run.log")
      val allocLog = new File(out, s"alloc-$run.log")
      val javaOptions = Seq(
        s"-Xloggc:${gcLog.getAbsolutePath }",
        "-XX:+PrintGCDetails", "-XX:+PrintGCDateStamps", "-XX:+PrintTenuringDistribution", "-XX:+PrintHeapAtGC"
      ) ++ (
        if (!doAlloc) Nil
        else "-javaagent:" + jar + s"=$allocLog $sampleSeconds " + trace.map{case (c,s) => s"$c:$s"}.mkString(",") :: Nil
        ) ++ vmArgs
      val runner = new ForkRun(ForkOptions(runJVMOptions = javaOptions))
      // NOTE: constraint is that the user cannot pass extra args
      toError(runner.run(main.get, data(cp), Nil, log))
      (gcLog, allocLog)
    }

    val (gcLogs, allocLogs) = logs.unzip

    gcLogs.map { gcLog =>
      GcParser.parse(fromFile(gcLog)) withEffect { events =>
        log.info(s"parsed ${events.size } garbage collection events")
      }
    } withEffect { processes =>
      GcReporter.gcReport(processes, new File(out, "gc.js"))
      toFile(new File(out, "gc.html"), fromRes("/com/github/fommil/lion/gc/report.html"))
    }

    allocLogs.map { allocLog =>
      AllocationParser.parse(fromFile(allocLog)) withEffect { events =>
        log.info(s"parsed ${events.size} allocation agent events")
      }
    } map {events => events.collect {
      case a: AllocationSizes if allocTrim.isDefined => a.trim(allocTrim.get)
      case a => a
    }
    } withEffect { processes =>
      AllocationReporter.allocReport(processes, new File(out, "alloc.js"))
      toFile(new File(out, "alloc.html"), fromRes("/com/github/fommil/lion/alloc/report.html"))
    }

    val report = new File(out, "report.html")
    toFile(report, fromRes("/lion-report.html"))
    log.info(s"lions-share report is available at ${report.getAbsolutePath}")
  }

}
