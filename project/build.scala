import sbt._
import Keys._
import org.sbtidea.SbtIdeaPlugin._

object LionBuild extends FommilBuild with Dependencies {

  override def projectOrg = "com.github.fommil.lion"
  override def projectVersion = "1.0-SNAPSHOT"

  lazy val agent = module("agent") settings (autoScalaLibrary := false, ideaIncludeScalaFacet := false)

  lazy val analysis = module("analysis") dependsOn (agent) settings (
    libraryDependencies ++= sprayjson :: commonsMaths :: akka :: logback :: scalatest)

  def modules = List(agent, top)
  def top = analysis
}

trait Dependencies {
  val bad = Seq(
    ExclusionRule(name = "log4j"),
    ExclusionRule(name = "commons-logging"),
    ExclusionRule(organization = "org.slf4j")
  )

  val scalatest = List(
    "org.scalatest" %% "scalatest" % "2.1.3" % "test",
    "org.scala-lang.modules" %% "scala-xml" % "1.0.1" % "test"
  )

  val sprayjson = "io.spray" %% "spray-json" % "1.2.6"
  val akka = "com.typesafe.akka" %% "akka-slf4j" % "2.3.0" excludeAll (bad: _*)

  val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

  val commonsMaths = "org.apache.commons" % "commons-math3" % "3.2"
}

trait FommilBuild extends Build {

  def projectVersion = "1.0-SNAPSHOT"
  def projectOrg = "com.github.fommil"
  def projectScala = "2.11.0-RC4"
  def modules: List[ProjectReference]
  def top: ProjectReference

  override val settings = super.settings ++ Seq(
    organization := projectOrg,
    version := projectVersion
  )
  def module(dir: String) = Project(id = dir, base = file(dir), settings = defaultSettings)

  import net.virtualvoid.sbt.graph.Plugin._
  lazy val defaultSettings = Defaults.defaultSettings ++ graphSettings ++ Seq(
    scalacOptions in Compile ++= Seq(
      "-encoding", "UTF-8", "-target:jvm-1.6", "-Xfatal-warnings",
      "-language:postfixOps", "-language:implicitConversions"),
    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:all", "-Xlint:-options", "-Werror"),
    outputStrategy := Some(StdoutOutput),
    fork := true,
    maxErrors := 1,
    resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("releases"),
      Resolver.typesafeRepo("releases"),
      Resolver.typesafeRepo("snapshots"),
      Resolver.sonatypeRepo("snapshots"),
      "spray" at "http://repo.spray.io/"
    ),
    ideaExcludeFolders := List(".idea", ".idea_modules"),
    scalaVersion := projectScala
  )

  // would be nice not to have to define the 'root'
  lazy val root = Project(id = "parent", base = file("."), settings = defaultSettings ++ Seq(ideaIgnoreModule := true)) aggregate (modules: _*) dependsOn (top)

}