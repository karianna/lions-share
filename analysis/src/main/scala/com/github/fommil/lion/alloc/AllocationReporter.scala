package com.github.fommil.lion.alloc

import com.github.fommil.utils.JsSupport
import java.io.{FileWriter, File}
import spray.json.DefaultJsonProtocol
import DefaultJsonProtocol._
import com.github.fommil.google.DataTableMarshalling._
import com.github.fommil.lion.alloc.AllocationAnalyserMarshalling._
import AllocationAnalyser.StdLib

object AllocationReporter extends JsSupport {
  private val analyser = new AllocationAnalyser

  // report on multiple JVM runs of the same process
  def allocReport(processes: Seq[AllocationSnapshots], out: File) = {
    implicit val writer = new FileWriter(out)
    try {
      jsVar("allocSizes", analyser.combinedAllocationSizes(processes))
      jsVar("allocTraces", analyser.combinedAllocationTraces(processes)(StdLib))
      jsVar("allocLengths", analyser.combinedAllocationLengths(processes))
    } finally writer.close()
  }
}
