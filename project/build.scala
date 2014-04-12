import sbt._
import Keys._
import net.virtualvoid.sbt.graph.Plugin._

object LionBuild extends Build {

  override val settings = super.settings ++ Seq(
    organization := "com.github.fommil.lion",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.0-RC4"
  )

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
      Resolver.sonatypeRepo("snapshots")
    ),
    parallelExecution in Test := false
  )
  
  def module(dir: String) = Project(id = dir, base = file(dir), settings = defaultSettings)
  import Dependencies._

  lazy val agent = module("agent") settings(
        autoScalaLibrary := false
  )

  lazy val analysis = module("analysis") dependsOn (agent) settings (
    libraryDependencies += scalatest % "test"
  )

  lazy val root = Project(id = "parent", base = file("."), settings = defaultSettings) aggregate (
      agent, analysis
    ) dependsOn (analysis) // yuck
}

object Dependencies {
  // to help resolve transitive problems, type:
  //   `sbt dependency-graph`
  //   `sbt test:dependency-tree`
  val bad = Seq(
    ExclusionRule(name = "log4j"),
    ExclusionRule(name = "commons-logging"),
    ExclusionRule(organization = "org.slf4j")
  )

  val scalatest = "org.scalatest" %% "scalatest" % "2.1.3"

}