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

  // report on multiple JVM runs of the same process
  def gcReport(processes: Seq[GcEvents], out: File) = {
    implicit val writer = new FileWriter(out)
    try {
      jsVar("gcAllocationRateData", analyser.averageAllocations(processes))
      jsVar("gcPausesData", analyser.averagePauses(processes))
      jsVar("gcThroughputData", analyser.throughput(processes))
      jsVar("gcMemoryData", analyser.averageProfile(processes))
    } finally writer.close()
  }
}
