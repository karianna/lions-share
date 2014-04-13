import sbt._
import Keys._
import sbt.Attributed.data

object LionPlugin extends Plugin {

  val lion = TaskKey[Unit]("lion", "Run a main class with lions-share profiling.")

  // https://github.com/sbt/sbt/issues/1260
  val agent = "com.github.fommil.lion" % "agent" % "1.0-SNAPSHOT"

  override val projectSettings = Seq(
    libraryDependencies += agent,
    lion <<= (
      fullClasspath in Runtime,
      mainClass in Runtime,
      streams in Runtime,
      update in Runtime
    ) map runLion
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
              update: UpdateReport): Unit = {
    // TODO: take user parameters (e.g. number of repeats, profiling settings)
    val args = Nil

    val jar = agentJar(update)
    val javaOptions = Seq(
      "-Xloggc:gc.log", "-XX:+PrintGCDetails", "-XX:+PrintGCDateStamps",
      "-XX:+PrintTenuringDistribution", "-XX:+PrintHeapAtGC",
      "-Dhack=" + jar
      // IMPL: pass the jar as the agent
    )

    // TODO: when None, prompt user
    val m = main.getOrElse("Scratch") // HACK

    println(s"RUNNING $m ${args.mkString}")

    val runner = new ForkRun(ForkOptions(runJVMOptions = javaOptions))
    toError(runner.run(m, data(cp), args, streams.log))

    // IMPL: post-process and produce the report

    println("FINISHED")
  }

}
