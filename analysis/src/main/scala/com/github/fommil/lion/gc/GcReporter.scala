package com.github.fommil.lion.gc

import java.io.{Writer, FileWriter, File}
import com.github.fommil.google.DataTableMarshalling._
import spray.json.{JsonWriter, JsValue}

object GcReporter {
  private val analyser = new GcAnalyser
  private implicit def toJsValue[T](a: T)(implicit w: JsonWriter[T]) = w.write(a)

  private def jsVar(name: String, value: JsValue)(implicit w: Writer) {
    w.append(s"var $name = ")
    w.append(value.compactPrint)
    w.append(";\n")
  }

  def gcReport(events: GcEvents, out: File) = {
    implicit val writer = new FileWriter(out)
    try {
      jsVar("gcAllocationsData", analyser.allocations(events))
      jsVar("gcPausesData", analyser.pauses(events))
      jsVar("gcMemoryData", analyser.profile(events))
    } finally writer.close()
  }

  // report on multiple JVM runs of the same process
  // processes are indexed by an id for display
  def gcReport(processes: Map[String, GcEvents], out: File) = {
    val events = processes.values.toSeq
    implicit val writer = new FileWriter(out)
    try {
      jsVar("gcAllocationRateData", analyser.averageAllocations(events))
      jsVar("gcPausesData", analyser.averagePauses(events))
      jsVar("gcThroughputData", analyser.throughput(processes))
      jsVar("gcMemoryData", analyser.averageProfile(events))
    } finally writer.close()
  }
}
