package com.github.fommil.lion.gc

import org.scalatest.FunSuite
import com.github.fommil.utils.StringGzResourceSupport

// (yeah, I'm being lazy... we should compare against previous JSON and account for rounding errors)
class GcAnalyserTest extends FunSuite with StringGzResourceSupport {

  val gcLog = fromGzRes("gc-jdk6.25-default.log.gz")
  val events = GcParser.parse(gcLog)

  val analyzer = new GcAnalyser

  test("pauses") {
    analyzer.averagePauses(events :: Nil)
    analyzer.averagePauses(Nil)
  }

  test("throughput") {
    analyzer.throughput(Map("blah" -> events))
    analyzer.throughput(Map("blah" -> Nil))
  }

  test("allocations") {
    analyzer.averageAllocations(events :: Nil)
    analyzer.averageAllocations(Nil)
  }

  test("profile") {
    analyzer.averageProfile(events :: Nil)
    analyzer.averageProfile(Nil)
  }
}