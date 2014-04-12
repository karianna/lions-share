package com.github.fommil.lion.gc

import org.scalatest.FunSuite
import com.github.fommil.utils.GzResourceSupport

// (yeah, I'm being lazy... we should compare against previous JSON and account for rounding errors)
class GcAnalyserTest extends FunSuite with GzResourceSupport {

  val gcLog = fromGzRes("gc-jdk6.25-default.log.gz")
  val events = GcParser.parse(gcLog)

  val analyzer = new GcAnalyser

  test("pauses") {
    analyzer.averagePauses(events :: Nil)
    analyzer.pauses(events)
  }

  test("throughput") {
    analyzer.throughput(Map("blah" -> events))
  }

  test("allocations") {
    analyzer.allocations(events take 100)
    analyzer.averageAllocations(events :: Nil)
  }

  test("profile") {
    analyzer.averageProfile(events :: Nil)
  }
}