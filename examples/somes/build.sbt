scalaVersion := "2.10.4"

javaOptions := Seq("-XX:+UseConcMarkSweepGC")

// number of times to repeat the un-instrumented runs 
lionRuns := 5

// disable the allocation agent runs
// (ridiculously slow for this example, which exaggerates churning)
lionAllocRuns := 0

// if lionAllocRuns is > 0, this is how we turn off tracing
//lionAllocTrace := Map.empty[String, Long]

