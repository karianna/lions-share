package com.github.fommil.lion.gc

import org.scalatest.FunSuite
import GcParser.{parse, parseAtom}
import MemoryRegion._
import com.github.fommil.utils.GzResourceSupport

class GcParserTest extends FunSuite with GzResourceSupport {

  test("read JDK 1.6.0_25 default GC atom") {
    val atom = """Heap before GC invocations=2 (full 0):
                 | PSYoungGen      total 4587520K, used 3950340K [0x00000006c0000000, 0x0000000800000000, 0x0000000800000000)
                 |  eden space 3932160K, 100% used [0x00000006c0000000,0x00000007b0000000,0x00000007b0000000)
                 |  from space 655360K, 2% used [0x00000007b0000000,0x00000007b11c11b0,0x00000007d8000000)
                 |  to   space 655360K, 0% used [0x00000007d8000000,0x00000007d8000000,0x0000000800000000)
                 | PSOldGen        total 10485760K, used 0K [0x0000000440000000, 0x00000006c0000000, 0x00000006c0000000)
                 |  object space 10485760K, 0% used [0x0000000440000000,0x0000000440000000,0x00000006c0000000)
                 | PSPermGen       total 36608K, used 36482K [0x0000000420000000, 0x00000004223c0000, 0x0000000440000000)
                 |  object space 36608K, 99% used [0x0000000420000000,0x00000004223a0a58,0x00000004223c0000)
                 |2014-03-02T10:24:49.245+0000: 17.549: [GC
                 |Desired survivor size 671088640 bytes, new threshold 7 (max 15)
                 | [PSYoungGen: 3950340K->15924K(4587520K)] 3950340K->15924K(15073280K), 0.0087500 secs] [Times: user=0.14 sys=0.01, real=0.01 secs]
                 |Heap after GC invocations=2 (full 0):
                 | PSYoungGen      total 4587520K, used 15924K [0x00000006c0000000, 0x0000000800000000, 0x0000000800000000)
                 |  eden space 3932160K, 0% used [0x00000006c0000000,0x00000006c0000000,0x00000007b0000000)
                 |  from space 655360K, 2% used [0x00000007d8000000,0x00000007d8f8d1c0,0x0000000800000000)
                 |  to   space 655360K, 0% used [0x00000007b0000000,0x00000007b0000000,0x00000007d8000000)
                 | PSOldGen        total 10485760K, used 0K [0x0000000440000000, 0x00000006c0000000, 0x00000006c0000000)
                 |  object space 10485760K, 0% used [0x0000000440000000,0x0000000440000000,0x00000006c0000000)
                 | PSPermGen       total 36608K, used 36482K [0x0000000420000000, 0x00000004223c0000, 0x0000000440000000)
                 |  object space 36608K, 99% used [0x0000000420000000,0x00000004223a0a58,0x00000004223c0000)""".stripMargin

    val events = parseAtom(atom)
    val start = Timestamp.parse("2014-03-02T10:24:49.245+0000")
    val end = Timestamp.parse("2014-03-02T10:24:49.253+0000")
    val interval = TimeInterval(start, end)
    assert(events.count(_.isInstanceOf[GcCollection]) === 3)
    assert(events.exists(_.isInstanceOf[GcSurvivors]))
    assert(events.exists(_.isInstanceOf[GcSnapshot]))

    events.foreach {
      case GcCollection(2, `interval`, Eden, MemoryUsage(4026531840L, 1.0), MemoryUsage(4026531840L, 0.0), false) =>
      case GcCollection(2, `interval`, From, MemoryUsage(671088640L, 0.02), MemoryUsage(671088640L, 0.02), false) =>
      case GcCollection(2, `interval`, To, MemoryUsage(671088640L, 0.0), MemoryUsage(671088640L, 0.0), false) =>
      case GcSurvivors(2, TimeInterval(`end`, `end`), 7, pop) if pop.isEmpty =>
      case GcSnapshot(2, TimeInterval(`start`, `start`), Tenured, MemoryUsage(10737418240L, 0.0)) =>
      case GcSnapshot(2, TimeInterval(`start`, `start`), Perm, MemoryUsage(37486592L, 0.9965581293706294)) =>
      case GcSnapshot(2, TimeInterval(`end`, `end`), Tenured, MemoryUsage(10737418240L, 0.0)) =>
      case GcSnapshot(2, TimeInterval(`end`, `end`), Perm, MemoryUsage(37486592L, 0.9965581293706294)) =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 default Full GC atom") {
    val atom = """Heap before GC invocations=139 (full 2):
                 | PSYoungGen      total 4293568K, used 14360K [0x00000006c0000000, 0x0000000800000000, 0x0000000800000000)
                 |  eden space 3350592K, 0% used [0x00000006c0000000,0x00000006c0000000,0x000000078c810000)
                 |  from space 942976K, 1% used [0x000000078c810000,0x000000078d616290,0x00000007c60f0000)
                 |  to   space 942656K, 0% used [0x00000007c6770000,0x00000007c6770000,0x0000000800000000)
                 | PSOldGen        total 10485760K, used 10291058K [0x0000000440000000, 0x00000006c0000000, 0x00000006c0000000)
                 |  object space 10485760K, 98% used [0x0000000440000000,0x00000006b41dcaf0,0x00000006c0000000)
                 | PSPermGen       total 220096K, used 110746K [0x0000000420000000, 0x000000042d6f0000, 0x0000000440000000)
                 |  object space 220096K, 50% used [0x0000000420000000,0x0000000426c26a90,0x000000042d6f0000)
                 |2014-03-02T10:49:37.822+0000: 1506.127: [Full GC [PSYoungGen: 14360K->0K(4293568K)] [PSOldGen: 10291058K->7880707K(10485760K)] 10305419K->7880707K(14779328K) [PSPermGen: 110746K->110746K(247936K)], 32.6574900 secs] [Times: user=32.65 sys=0.00, real=32.65 secs]
                 |Heap after GC invocations=139 (full 2):
                 | PSYoungGen      total 4293568K, used 0K [0x00000006c0000000, 0x0000000800000000, 0x0000000800000000)
                 |  eden space 3350592K, 0% used [0x00000006c0000000,0x00000006c0000000,0x000000078c810000)
                 |  from space 942976K, 0% used [0x000000078c810000,0x000000078c810000,0x00000007c60f0000)
                 |  to   space 942656K, 0% used [0x00000007c6770000,0x00000007c6770000,0x0000000800000000)
                 | PSOldGen        total 10485760K, used 7880707K [0x0000000440000000, 0x00000006c0000000, 0x00000006c0000000)
                 |  object space 10485760K, 75% used [0x0000000440000000,0x0000000621000d08,0x00000006c0000000)
                 | PSPermGen       total 247936K, used 110746K [0x0000000420000000, 0x000000042f220000, 0x0000000440000000)
                 |  object space 247936K, 44% used [0x0000000420000000,0x0000000426c26a90,0x000000042f220000)""".stripMargin

    val events = parseAtom(atom)
    val start = Timestamp.parse("2014-03-02T10:49:37.822+0000")
    val end = Timestamp.parse("2014-03-02T10:50:10.479+0000")
    val interval = TimeInterval(start, end)
    assert(events.size === 5, events)

    events.foreach {
      case GcCollection(139, `interval`, Eden, MemoryUsage(3431006208L, 0.0), MemoryUsage(3431006208L, 0.0), true) =>
      case GcCollection(139, `interval`, From, MemoryUsage(965607424L, 0.01), MemoryUsage(965607424L, 0.0), true) =>
      case GcCollection(139, `interval`, To, MemoryUsage(965279744L, 0.0), MemoryUsage(965279744L, 0.0), true) =>
      case GcCollection(139, `interval`, Tenured, MemoryUsage(10737418240L, 0.9814317703247071), MemoryUsage(10737418240L, 0.7515627861022949), true) =>
      case GcCollection(139, `interval`, Perm, MemoryUsage(225378304L, 0.5031713434137831), MemoryUsage(253886464L, 0.4466717217346412), true) =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 logs with default GC") {
    val gcLog = fromGzRes("gc-jdk6.25-default.log.gz")
    assert(parse(gcLog).size === 2812)
  }

  test("read JDK 1.6.0_25 CMS in incremental mode atom") {
    val atom = """Heap before GC invocations=14 (full 2):
                 | par new generation   total 440896K, used 401469K [0x0000000660000000, 0x000000067de60000, 0x000000067de60000)
                 |  eden space 391936K, 100% used [0x0000000660000000, 0x0000000677ec0000, 0x0000000677ec0000)
                 |  from space 48960K,  19% used [0x0000000677ec0000, 0x000000067880f480, 0x000000067ae90000)
                 |  to   space 48960K,   0% used [0x000000067ae90000, 0x000000067ae90000, 0x000000067de60000)
                 | concurrent mark-sweep generation total 5801600K, used 8577K [0x000000067de60000, 0x00000007e0000000, 0x00000007e0000000)
                 | concurrent-mark-sweep perm gen total 74756K, used 44741K [0x00000007e0000000, 0x00000007e4901000, 0x0000000800000000)
                 |2014-03-04T10:50:52.163+0000: 21.911: [GC 21.911: [ParNew
                 |Desired survivor size 25067520 bytes, new threshold 4 (max 4)
                 |- age   1:    6002208 bytes,    6002208 total
                 |- age   3:        304 bytes,    6002512 total
                 |- age   4:      18672 bytes,    6021184 total
                 |: 401469K->8314K(440896K), 0.0031380 secs] 410046K->16892K(6242496K) icms_dc=0 , 0.0031990 secs] [Times: user=0.05 sys=0.01, real=0.00 secs]
                 |Heap after GC invocations=15 (full 2):
                 | par new generation   total 440896K, used 8314K [0x0000000660000000, 0x000000067de60000, 0x000000067de60000)
                 |  eden space 391936K,   0% used [0x0000000660000000, 0x0000000660000000, 0x0000000677ec0000)
                 |  from space 48960K,  16% used [0x000000067ae90000, 0x000000067b6ae8e0, 0x000000067de60000)
                 |  to   space 48960K,   0% used [0x0000000677ec0000, 0x0000000677ec0000, 0x000000067ae90000)
                 | concurrent mark-sweep generation total 5801600K, used 8577K [0x000000067de60000, 0x00000007e0000000, 0x00000007e0000000)
                 | concurrent-mark-sweep perm gen total 74756K, used 44741K [0x00000007e0000000, 0x00000007e4901000, 0x0000000800000000)""".stripMargin
    val events = parseAtom(atom)

    val start = Timestamp.parse("2014-03-04T10:50:52.163+0000")
    val end = Timestamp.parse("2014-03-04T10:50:52.166+0000")
    val interval = TimeInterval(start, end)
    assert(events.count(_.isInstanceOf[GcCollection]) === 3)
    assert(events.exists(_.isInstanceOf[GcSurvivors]))
    assert(events.exists(_.isInstanceOf[GcSnapshot]))
    events.foreach {
      case GcCollection(14, `interval`, Eden, MemoryUsage(401342464L, 1.0), MemoryUsage(401342464L, 0.0), false) =>
      case GcCollection(14, `interval`, From, MemoryUsage(50135040L, 0.19), MemoryUsage(50135040L, 0.16), false) =>
      case GcCollection(14, `interval`, To, MemoryUsage(50135040L, 0), MemoryUsage(50135040L, 0), false) =>
      case GcSnapshot(14, TimeInterval(`start`, `start`), Tenured, MemoryUsage(5940838400L, 0.00147838527302813)) =>
      case GcSnapshot(14, TimeInterval(`end`, `end`), Tenured, MemoryUsage(5940838400L, 0.00147838527302813)) =>
      case GcSnapshot(14, TimeInterval(`start`, `start`), Perm, MemoryUsage(76550144L, 0.5984937663866445)) =>
      case GcSnapshot(14, TimeInterval(`end`, `end`), Perm, MemoryUsage(76550144L, 0.5984937663866445)) =>
      case GcSurvivors(14, TimeInterval(`end`, `end`), 4, pop) if pop(1) == 6002208L & pop(3) == 304L & pop(4) == 18672L =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 atom containing multiple CMS marks (incremental mode)") {
    val atom =
      """2014-03-04T10:50:40.865+0000: 10.613: [GC [1 CMS-initial-mark: 0K(5801600K)] 211090K(6242496K), 0.2403620 secs] [Times: user=0.24 sys=0.00, real=0.24 secs]
        |2014-03-04T10:50:41.105+0000: 10.853: [CMS-concurrent-mark-start]
        |2014-03-04T10:50:41.116+0000: 10.864: [CMS-concurrent-mark: 0.011/0.011 secs] [Times: user=0.08 sys=0.00, real=0.01 secs]
        |2014-03-04T10:50:41.116+0000: 10.864: [CMS-concurrent-preclean-start]
        |2014-03-04T10:50:41.130+0000: 10.878: [CMS-concurrent-preclean: 0.014/0.014 secs] [Times: user=0.03 sys=0.00, real=0.01 secs]
        |2014-03-04T10:50:41.130+0000: 10.878: [CMS-concurrent-abortable-preclean-start]""".stripMargin

    val events = parseAtom(atom)
    assert(events.size == 6)
    assert(events.forall(_.isInstanceOf[GcMark]))
  }

  test("read JDK 1.6.0_25 logs with CMS in incremental mode and full GC") {
    val atom =
      """Heap before GC invocations=267 (full 21):
        | par new generation   total 15099520K, used 15099520K [0x00002aaaae0e0000, 0x00002aaeae0e0000, 0x00002aaeae0e0000)
        |  eden space 13421824K, 100% used [0x00002aaaae0e0000, 0x00002aade1420000, 0x00002aade1420000)
        |  from space 1677696K, 100% used [0x00002aae47a80000, 0x00002aaeae0e0000, 0x00002aaeae0e0000)
        |  to   space 1677696K,   0% used [0x00002aade1420000, 0x00002aade1420000, 0x00002aae47a80000)
        | concurrent mark-sweep generation total 50331648K, used 48449796K [0x00002aaeae0e0000, 0x00002abaae0e0000, 0x00002abaae0e0000)
        | concurrent-mark-sweep perm gen total 214452K, used 128641K [0x00002abaae0e0000, 0x00002ababb24d000, 0x00002abace0e0000)
        |2014-03-19T12:58:39.412+0000: 1915.201: [GC 1915.202: [ParNew (promotion failed)
        |Desired survivor size 858980352 bytes, new threshold 1 (max 15)
        |- age   1: 1144689112 bytes, 1144689112 total
        |: 15099520K->15000718K(15099520K), 4.2724620 secs]1919.474: [CMS2014-03-19T12:59:52.948+0000: 1988.738: [CMS-concurrent-sweep: 71.501/78.120 secs] [Times: user=131.81 sys=0.30, real=78.11 secs]
        | (concurrent mode failure): 49073610K->13579894K(50331648K), 148.4742930 secs] 63549316K->13579894K(65431168K), [CMS Perm : 128641K->128608K(214452K)], 152.7470620 secs] [Times: user=161.63 sys=0.14, real=152.72 secs]
        |Heap after GC invocations=268 (full 22):
        | par new generation   total 15099520K, used 0K [0x00002aaaae0e0000, 0x00002aaeae0e0000, 0x00002aaeae0e0000)
        |  eden space 13421824K,   0% used [0x00002aaaae0e0000, 0x00002aaaae0e0000, 0x00002aade1420000)
        |  from space 1677696K,   0% used [0x00002aade1420000, 0x00002aade1420000, 0x00002aae47a80000)
        |  to   space 1677696K,   0% used [0x00002aae47a80000, 0x00002aae47a80000, 0x00002aaeae0e0000)
        | concurrent mark-sweep generation total 50331648K, used 13579894K [0x00002aaeae0e0000, 0x00002abaae0e0000, 0x00002abaae0e0000)
        | concurrent-mark-sweep perm gen total 214452K, used 128608K [0x00002abaae0e0000, 0x00002ababb24d000, 0x00002abace0e0000)""".stripMargin

    val events = parseAtom(atom)
    val start = Timestamp.parse("2014-03-19T12:58:39.412+0000")
    val end = Timestamp.parse("2014-03-19T13:01:12.159+0000")
    val interval = TimeInterval(start, end)
    assert(events.count(_.isInstanceOf[GcCollection]) === 5)
    assert(events.exists(_.isInstanceOf[GcSurvivors]))
    assert(!events.exists(_.isInstanceOf[GcSnapshot]))

    events.foreach {
      case GcCollection(267, `interval`, Eden, MemoryUsage(13743947776L, 1.0), MemoryUsage(13743947776L, 0.0), true) =>
      case GcCollection(267, `interval`, From, MemoryUsage(1717960704L, 1.0), MemoryUsage(1717960704L, 0.0), true) =>
      case GcCollection(267, `interval`, To, MemoryUsage(1717960704L, 0), MemoryUsage(1717960704L, 0), true) =>
      case GcCollection(267, `interval`, Tenured, MemoryUsage(51539607552L, 0.9626109600067139), MemoryUsage(51539607552L, 0.26980825265248615), true) =>
      case GcCollection(267, `interval`, Perm, MemoryUsage(219598848L, 0.5998591759461325), MemoryUsage(219598848L, 0.5997052953574693), true) =>
      case GcSurvivors(267, TimeInterval(`end`, `end`), 1, pop) if pop(1) == 1144689112L =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 logs with CMS and Full GC variant") {
    val atom = """Heap before GC invocations=356 (full 0):
                 | par new generation   total 440896K, used 258775K [0x00002aaaae0e0000, 0x00002aaacbf40000, 0x00002aaacbf40000)
                 |  eden space 391936K,  54% used [0x00002aaaae0e0000, 0x00002aaabb137590, 0x00002aaac5fa0000)
                 |  from space 48960K,  92% used [0x00002aaac5fa0000, 0x00002aaac8bfe6f0, 0x00002aaac8f70000)
                 |  to   space 48960K,   0% used [0x00002aaac8f70000, 0x00002aaac8f70000, 0x00002aaacbf40000)
                 | concurrent mark-sweep generation total 41453184K, used 4862938K [0x00002aaacbf40000, 0x00002ab4ae0e0000, 0x00002ab4ae0e0000)
                 | concurrent-mark-sweep perm gen total 94784K, used 94486K [0x00002ab4ae0e0000, 0x00002ab4b3d70000, 0x00002ab4ce0e0000)
                 |2014-03-31T09:33:01.713+0100: 192.038: [Full GC 192.039: [CMS: 4862938K->1986236K(41453184K), 5.6509070 secs] 5121713K->1986236K(41894080K), [CMS Perm : 94486K->94267K(94784K)], 5.6511660 secs] [Times: user=5.44 sys=0.21, real=5.65 secs]
                 |Heap after GC invocations=357 (full 1):
                 | par new generation   total 440896K, used 0K [0x00002aaaae0e0000, 0x00002aaacbf40000, 0x00002aaacbf40000)
                 |  eden space 391936K,   0% used [0x00002aaaae0e0000, 0x00002aaaae0e0000, 0x00002aaac5fa0000)
                 |  from space 48960K,   0% used [0x00002aaac5fa0000, 0x00002aaac5fa0000, 0x00002aaac8f70000)
                 |  to   space 48960K,   0% used [0x00002aaac8f70000, 0x00002aaac8f70000, 0x00002aaacbf40000)
                 | concurrent mark-sweep generation total 41453184K, used 1986236K [0x00002aaacbf40000, 0x00002ab4ae0e0000, 0x00002ab4ae0e0000)
                 | concurrent-mark-sweep perm gen total 157116K, used 94267K [0x00002ab4ae0e0000, 0x00002ab4b7a4f000, 0x00002ab4ce0e0000)""".stripMargin
    val events = parseAtom(atom)

    val start = Timestamp.parse("2014-03-31T09:33:01.713+0100")
    val end = Timestamp.parse("2014-03-31T09:33:07.364+0100")
    val interval = TimeInterval(start, end)
    assert(events.count(_.isInstanceOf[GcCollection]) === 5)
    assert(!events.exists(_.isInstanceOf[GcSurvivors]))
    assert(!events.exists(_.isInstanceOf[GcSnapshot]))

    events.foreach {
      case GcCollection(356, `interval`, Eden, MemoryUsage(401342464L, 0.54), MemoryUsage(401342464L, 0.0), true) =>
      case GcCollection(356, `interval`, From, MemoryUsage(50135040L, 0.92), MemoryUsage(50135040L, 0.0), true) =>
      case GcCollection(356, `interval`, To, MemoryUsage(50135040L, 0), MemoryUsage(50135040L, 0), true) =>
      case GcCollection(356, `interval`, Tenured, MemoryUsage(42448060416L, 0.11731156767113474), MemoryUsage(42448060416L, 0.04791516135407114), true) =>
      case GcCollection(356, `interval`, Perm, MemoryUsage(97058816L, 0.9968560094530723), MemoryUsage(160886784L, 0.5999834517172026), true) =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 logs with CMS and weird mark interspersal") {
    val atom = """Heap before GC invocations=675 (full 1):
                 | par new generation   total 440896K, used 440896K [0x00002aaaae0e0000, 0x00002aaacbf40000, 0x00002aaacbf40000)
                 |  eden space 391936K, 100% used [0x00002aaaae0e0000, 0x00002aaac5fa0000, 0x00002aaac5fa0000)
                 |  from space 48960K, 100% used [0x00002aaac8f70000, 0x00002aaacbf40000, 0x00002aaacbf40000)
                 |  to   space 48960K,   0% used [0x00002aaac5fa0000, 0x00002aaac5fa0000, 0x00002aaac8f70000)
                 | concurrent mark-sweep generation total 41453184K, used 21812342K [0x00002aaacbf40000, 0x00002ab4ae0e0000, 0x00002ab4ae0e0000)
                 | concurrent-mark-sweep perm gen total 95936K, used 95480K [0x00002ab4ae0e0000, 0x00002ab4b3e90000, 0x00002ab4ce0e0000)
                 |2014-03-31T09:34:09.470+0100: 270.121: [GC 270.122: [ParNew2014-03-31T09:34:09.558+0100: 270.210: [CMS-concurrent-abortable-preclean: 0.331/0.433 secs] [Times: user=2.18 sys=0.07, real=0.43 secs]
                 |
                 |Desired survivor size 25067520 bytes, new threshold 1 (max 4)
                 |- age   1:   50133488 bytes,   50133488 total
                 |: 440896K->48960K(440896K), 0.0984970 secs] 22253238K->21988340K(41894080K), 0.0986450 secs] [Times: user=1.52 sys=0.07, real=0.10 secs]
                 |Heap after GC invocations=676 (full 1):
                 | par new generation   total 440896K, used 48960K [0x00002aaaae0e0000, 0x00002aaacbf40000, 0x00002aaacbf40000)
                 |  eden space 391936K,   0% used [0x00002aaaae0e0000, 0x00002aaaae0e0000, 0x00002aaac5fa0000)
                 |  from space 48960K, 100% used [0x00002aaac5fa0000, 0x00002aaac8f70000, 0x00002aaac8f70000)
                 |  to   space 48960K,   0% used [0x00002aaac8f70000, 0x00002aaac8f70000, 0x00002aaacbf40000)
                 | concurrent mark-sweep generation total 41453184K, used 21939380K [0x00002aaacbf40000, 0x00002ab4ae0e0000, 0x00002ab4ae0e0000)
                 | concurrent-mark-sweep perm gen total 95936K, used 95480K [0x00002ab4ae0e0000, 0x00002ab4b3e90000, 0x00002ab4ce0e0000)""".stripMargin
    val events = parseAtom(atom)

    val start = Timestamp.parse("2014-03-31T09:34:09.470+0100")
    val end = Timestamp.parse("2014-03-31T09:34:09.568+0100")
    val interval = TimeInterval(start, end)
    assert(events.count(_.isInstanceOf[GcCollection]) === 3)
    assert(events.exists(_.isInstanceOf[GcSurvivors]))
    assert(events.exists(_.isInstanceOf[GcSnapshot]))

    events.foreach {
      case GcCollection(675, `interval`, _, _, _, false) =>
      case GcSnapshot(675, TimeInterval(`start`, `start`), _, _) =>
      case GcSnapshot(675, TimeInterval(`end`, `end`), _, _) =>
      case GcSurvivors(675, TimeInterval(`end`, `end`), 1, pop) if pop(1) == 50133488L =>
      case e => fail("unexpected " + e)
    }
  }

  test("read JDK 1.6.0_25 logs with YG occupancy") {
    val atom = """2014-03-31T09:34:09.569+0100: 270.221: [GC[YG occupancy: 52086 K (440896 K)]270.221: [Rescan (parallel) , 0.0141040 secs]270.235: [weak refs processing, 0.0003200 secs] [1 CMS-remark: 21939380K(41453184K)] 21991467K(41894080K), 0.0145820 secs] [Times: user=0.31 sys=0.00, real=0.02 secs]"""
    assert(parseAtom(atom).size === 1)
  }

  test("read JDK 1.6.0_25 logs with CMS in incremental mode") {
    val gcLog = fromGzRes("gc-jdk6.25-cmsinc.log.gz")
    val events = parse(gcLog)
    assert(events.size === 3407)
  }

  test("read JDK 1.6.0_25 logs with CMS and no incremental mode") {
    // note: this logfile has a partial log at the end
    // (logging it at INFO noise is the correct behaviour)
    val gcLog = fromGzRes("gc-jdk6.25-cms.log.gz")
    val events = parse(gcLog)
    assert(events.size === 8901)
  }

  test("read JDK 1.7.0_51 logs with CMS") {
    val gcLog = fromGzRes("gc-jdk7.51-cms.log.gz")
    val events = parse(gcLog)
    assert(events.size === 208)
  }

  test("read JDK 1.7.0_51 logs with default") {
    val gcLog = fromGzRes("gc-jdk7.51-default.log.gz")
    val events = parse(gcLog)
    assert(events.size === 189)
  }

  ignore("serialise GcEvents with Spray JSON") {
    fail("not implemented")
    //    val gcLog = fromGzRes("gc-jdk6.25-cms.log.gz")
    //    val events = parse(gcLog)
    //
    //    import net.liftweb.json._
    //    import net.liftweb.json.Serialization.{read, write}
    //    implicit val formats = DefaultFormats + ShortTypeHints(List(
    //              classOf[GcCollection], classOf[GcSnapshot], classOf[GcSurvivors], classOf[GcMark]
    //            ))
    //
    //    val start = Timestamp.parse("2014-03-31T09:33:01.713+0100")
    //    val end = Timestamp.parse("2014-03-31T09:33:07.364+0100")
    //    val collection = GcCollection(356, start, end, Eden, MemoryUsage(401342464L, 0.54), MemoryUsage(401342464L, 0.0), true)
    //    val snapshot = GcSnapshot(675, start, MemoryRegion.From, MemoryUsage(401342464L, 0.0))
    //    val survivors = GcSurvivors(675, end, 1, Map("1" -> 50133488L))
    //    val mark = GcMark(start, end, "blah", false)
    //
    //    assert(read[GcCollection](write(collection)) === collection)
    //
    //    assert(read[GcSnapshot](write(snapshot)) === snapshot)
    //
    //    assert(read[GcSurvivors](write(survivors)) === survivors)
    //
    //    assert(read[GcMark](write(mark)) === mark)
    //
    //    assert(read[List[GcEvent]](write(events)) == events)
  }
}
