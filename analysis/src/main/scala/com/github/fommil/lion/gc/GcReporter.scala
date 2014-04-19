package com.github.fommil.lion.gc

import java.io.{FileWriter, File}
import com.github.fommil.utils.JsSupport
import com.github.fommil.google.DataTableMarshalling._

object GcReporter extends JsSupport {
  private val analyser = new GcAnalyser

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
