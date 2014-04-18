organization := "com.github.fommil"

name := "tiger"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.4"

//javaOptions := Seq("-XX:+UseConcMarkSweepGC")


resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("snapshots"),
      Resolver.typesafeRepo("releases")
)
