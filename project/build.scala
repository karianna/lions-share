import sbt._
import Keys._
import net.virtualvoid.sbt.graph.Plugin._

object LionBuild extends Build {

  import Dependencies._

  override val settings = super.settings ++ Seq(
    organization := "com.github.fommil.lion",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.0-RC4"
  )
  def module(dir: String) = Project(id = dir, base = file(dir), settings = defaultSettings)

  lazy val defaultSettings = Defaults.defaultSettings ++ graphSettings ++ Seq(
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.6", "-deprecation", "-unchecked"),
    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:unchecked", "-Xlint:deprecation", "-Xlint:-options"),
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
    )
  )

  lazy val agent = module("agent") settings (
    autoScalaLibrary := false
    )
  lazy val analysis = module("analysis") dependsOn (agent) settings (
    libraryDependencies ++= sprayjson :: akka :: logback :: scalatest
    )

  // would be nice not to have to define the 'root'
  lazy val root = Project(id = "parent", base = file("."), settings = defaultSettings) aggregate(
    agent, analysis
    ) dependsOn analysis
}

object Dependencies {
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
}