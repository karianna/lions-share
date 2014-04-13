import sbt._
import sbt.ForkOptions
import sbt.Keys._
import sbt.Attributed.data
import complete.DefaultParsers._
import io.Source

import com.github.fommil
import fommil.utils.PimpedAny._
import com.github.fommil.lion.gc.{GcReporter, GcParser}

object LionPlugin extends Plugin {

  val lion = TaskKey[Unit]("lion", "Run a main class with lions-share profiling.")

  // https://github.com/sbt/sbt/issues/1260
  private val agent = "com.github.fommil.lion" % "agent" % "1.0-SNAPSHOT"

//  private val parser = IntBasic.examples("<arg>")

  override val projectSettings = Seq(
    libraryDependencies += agent,
    // TODO: plugin settings for repeats and so on
    lion := //Def.inputTask {
      //        val args = parser.parsed
      //        println("parsed " + args)
      // TODO: take arguments and parse them
      //    val ArgsRegex = """(\d+)\s*(\S+)""".r
      //    val (runs, tracers) = args match {
      //      case ArgsRegex(r, t) => (r.toInt, t.split(",").toList)
      //      case ArgsRegex(r) => (r.toInt, Nil)
      //      case _ => (10, Nil)
      //    }
      //    require(runs > 0, "first parameter must be >= 0")
      runLion(
        (fullClasspath in Runtime).value,
        (mainClass in Runtime).value,
        (streams in Runtime).value,
        (update in Runtime).value
      )
    //      }
  )

  def agentJar(update: UpdateReport): File = {
    for {
      report <- update.configuration("runtime-internal").get.modules
      module = report.module
      // == on agent doesn't seem to work
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
              update: UpdateReport): Unit = {
    val jar = agentJar(update)

    val log = streams.log

    // TODO: when None, prompt user
    val m = main.getOrElse("Scratch") // HACK

    val runs = 10
    log.info(s"running the lions-share $runs times for $m")

    val processes = (1 to runs) map { run =>
      val gcLog = new File(s"gc-$run.log")
      val javaOptions = Seq(
        s"-Xloggc:${gcLog.getAbsolutePath }",
        "-XX:+PrintGCDetails", "-XX:+PrintGCDateStamps", "-XX:+PrintTenuringDistribution", "-XX:+PrintHeapAtGC",
        "-Dhack=" + jar
        // IMPL: pass the jar as the agent
      )
      val runner = new ForkRun(ForkOptions(runJVMOptions = javaOptions))
      // NOTE: constraint is that the user cannot pass extra args
      // maybe we use settings to entirely define the run
      toError(runner.run(m, data(cp), Nil, log))

      val gcLogContents = {
        val source = Source.fromFile(gcLog)
        try source.getLines().mkString("\n")
        finally source.close()
        //        gcLog.delete()
      }
      GcParser.parse(gcLogContents) withEffect { events =>
        log.info(s"parsed ${events.size } garbage collection events")
      }
    }
    GcReporter.gcReport(processes, new File("data.js"))

    // IMPL: post-process and produce the report
  }

}
