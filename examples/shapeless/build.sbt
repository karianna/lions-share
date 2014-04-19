scalaVersion := "2.10.4"

//javaOptions := Seq("-XX:+UseConcMarkSweepGC")

resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("snapshots"),
      Resolver.typesafeRepo("releases")
)


libraryDependencies ++= Seq(
  "com.chuusai" % "shapeless" % "2.0.0" cross CrossVersion.full,
  "org.scala-lang" % "scala-compiler" % "2.10.4"
)
