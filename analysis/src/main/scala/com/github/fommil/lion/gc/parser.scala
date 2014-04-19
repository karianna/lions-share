package com.github.fommil.lion.gc

import org.parboiled.scala.parserunners.ReportingParseRunner
import org.parboiled.errors.ErrorUtils
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}
import akka.event.slf4j.SLF4JLogging
import org.parboiled.scala._
import com.github.fommil.utils.{TimeInterval, Timestamp, WhitespaceAwareParser}

object GcParser {
  def parse(source: String) = parser.parseGcLog(source).sorted

  private val parser = new GcParser
  protected[gc] def parseAtom(atom: String) = parser.parseGcAtom(atom)
}

/** Parses JDK GC log files that have been produced using flags:
  *
  * `-Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC`
  *
  * The following JDKs are supported:
  *
  * 1. 1.6.0_25
  * 2. 1.7.0_51
  * 3. 1.8.0_0
  *
  * The following garbage collectors are supported:
  *
  * 1. default GC (i.e. `-XX:+UseParallelGC`)
  * 2. default GC with parallel old gen, < 1.7.0: `-XX:+UseParallelOldGC`
  * 3. concurrent mark sweep: `-XX:+UseConcMarkSweepGC`
  * 4. concurrent mark sweep (incremental), < 1.8.0: `-XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode`
  *
  * @see https://blogs.oracle.com/poonam/entry/understanding_cms_gc_logs
  */
private class GcParser extends GcParserCommon with GcParserDefault with GcParserCms with SLF4JLogging {

  // this could be done streaming, but meh...
  def parseGcLog(source: String): GcEvents = source.split("[{}]").par.flatMap { atom =>
    if (atom.trim.isEmpty) Nil
    else Try(parseGcAtom(atom.trim)) match {
      case Success(events) => events
      case Failure(t) => Nil
    }
  }.toList

  def parseGcAtom(atom: String): GcEvents = {
    val parse = ReportingParseRunner((DefaultGc | CmsGc | Header) ~ EOI).run(atom)
    parse.result match {
      case None =>
        log.info("Could not parse this part of the log. " +
          "Please report this message (or the log), noting your version of Java, to " +
          "https://github.com/fommil/lions-share/issues\n" +
          ErrorUtils.printParseErrors(parse) + atom)
        Nil

      case Some(result) => result
    }
  }

  override val buildParseTree = true

}

trait GcParserCommon extends WhitespaceAwareParser {


  // abstract rules
  def nTimesMap[K, V](n: Int, rule: Rule1[Map[K, V]]): Rule1[Map[K, V]] = nTimes(n, rule) ~~> { _.reduce(_ ++ _) }

  def oneOrMoreSets[T](rule: Rule1[Set[T]]): Rule1[Set[T]] = oneOrMore(rule) ~~> { _.flatten.toSet }


  // common GC rules

  def Header: Rule1[GcEvents] = rule {
    "Java HotSpot(TM) " ~ oneOrMore(noneOf("\n")) ~ "\n" ~
    "Memory: " ~ oneOrMore(noneOf("\n")) ~ "\n" ~
    "CommandLine flags: " ~ oneOrMore(noneOf("\n"))
  } ~> {s => Nil}

  def SurvivorSummary: Rule1[Int] = rule {
    "Desired survivor size " ~ Digits ~ " bytes, new threshold " ~ SavedDigits ~ " (max " ~ Digits ~ ")\n"
  } ~~> { _.toInt }

  def MemoryRegionSnapshot: Rule1[Map[MemoryRegion, MemoryUsage]] = rule {
    TraditionalRegionSnapshot | MetaRegionSnapshot
  }

  def TraditionalRegionSnapshot: Rule1[Map[MemoryRegion, MemoryUsage]] = rule {
    " " ~ MemoryRegionName ~ " total " ~ SavedSize ~ ", used " ~ SavedSize ~ MemoryRegister ~
      zeroOrMore(MemorySubRegion)
  } ~~> { (name, totalSize, totalUsed, subRegions) =>
    if (subRegions.size <= 1)
      Map(MemoryRegions.fromLognames(name, "") -> MemoryUsage(totalSize, totalUsed.toDouble / totalSize))
    else
      subRegions.map { sub => (MemoryRegions.fromLognames(name, sub._1), MemoryUsage(sub._2, sub._3)) }.toMap
  }

  def MetaRegionSnapshot: Rule1[Map[MemoryRegion, MemoryUsage]] = rule {
    " Metaspace " ~ "used " ~ SavedSize ~ ", capacity " ~ SavedSize ~ ", committed " ~ Size ~ ", reserved " ~ Size ~
      " class space " ~ "used " ~ Size ~ ", capacity " ~ Size ~ ", committed " ~ Size ~ ", reserved " ~ Size ~ " "
  } ~~> { (used, total) =>
    Map(MemoryRegion.Perm -> MemoryUsage(total, used.toDouble / total))
  }

  def MemorySubRegion: Rule1[(String, Long, Double)] = rule {
    " " ~ MemorySubRegionName ~ " space " ~ SavedSize ~ ", " ~ Percentage ~ " used" ~ MemoryRegister
  } ~~> { (_, _, _) }

  def MemorySubRegionName: Rule1[String] = rule { "eden" | "from" | "to" | "object" } ~> identity

  def MemoryRegister: Rule0 = rule { " [" ~ Hex ~ ", " ~ Hex ~ ", " ~ Hex ~ ") " }

  def MemoryRegionName: Rule1[String] = rule {
    "PSYoungGen" | "PSOldGen" | "PSPermGen" | "ParOldGen" |
      "concurrent mark-sweep generation" | "par new generation" | "concurrent-mark-sweep perm gen" | "ParNew"
  } ~> identity

  def Times: Rule0 = rule {
    " [Times: user=" ~ Seconds ~ " sys=" ~ Seconds ~ ", real=" ~ Seconds ~ " secs] "
  }

  def CollectionSizes: Rule0 = rule {
    " " ~ Size ~ "->" ~ Size ~ "(" ~ Size ~ ") "
  }

  // low level rules
  def IsoDate: Rule1[Timestamp] = rule {
    Digits ~ "-" ~ Digits ~ "-" ~ Digits ~ "T" ~ Digits ~ ":" ~ Digits ~ ":" ~ Digits ~ "." ~ Digits ~ ("+" | "-") ~
      Digits
  } ~> { Timestamp.parse }

  def SavedSize: Rule1[Long] = rule { Digits ~> (_.toLong) ~ "K" } ~~> (_ * 1024L)

  def Percentage: Rule1[Double] = rule { Digits ~> (_.toDouble) ~ "%" } ~~> (_ / 100.0)

  def SavedSeconds: Rule1[Duration] = Seconds ~> (s => Duration(s.toDouble, SECONDS))

  def SavedDigits = rule { oneOrMore(Digit) } ~> (_.toLong)

  // below are pattern matchers for ignored or primitive types
  def Digits = rule { oneOrMore(Digit) }

  def Digit = rule { "0" - "9" }

  def Hex = rule { "0x" ~ oneOrMore(Digit | "a" - "f" | "A" - "F") }

  def Size = rule { Digits ~ " K" }

  def Seconds = rule { oneOrMore(Digit) ~ "." ~ oneOrMore(Digit) }
}

trait GcParserDefault {
  this: GcParserCommon =>

  def DefaultGc: Rule1[List[GcEvent]] = rule {
    NormalDefaultGc | FinalDefaultGc | DefaultGcHeap
  }

  def NormalDefaultGc: Rule1[List[GcEvent]] = rule {
    "Heap before GC invocations=" ~ SavedDigits ~ " (full " ~ Digits ~ "):\n" ~ nTimesMap(3, MemoryRegionSnapshot) ~
      IsoDate ~ ": " ~ Seconds ~ ": " ~ CollectionSummary ~
      "Heap after GC invocations=" ~ Digits ~ " (full " ~ Digits ~ "):\n" ~ nTimesMap(3, MemoryRegionSnapshot)
  } ~~> { (id, before, from, summary, after) =>
    val (full, survivors, regionNames, duration) = summary
    val regions = regionNames.toList.flatMap(MemoryRegions.fromLogname)
    val to = from + duration

    regions.map { region =>
      GcCollection(id, TimeInterval(from, to), region, before(region), after(region), full)
    } ++ MemoryRegions.ALL.flatMap { region =>
      if (regions.contains(region)) Nil
      else GcSnapshot(id, TimeInterval(from, from), region, before(region)) ::
        GcSnapshot(id, TimeInterval(to, to), region, after(region)) :: Nil
    } ++ {
      survivors match {
        case None => Nil
        case Some(threshold) => GcSurvivors(id, TimeInterval(to, to), threshold) :: Nil
      }
    }
  }

  // sometimes appears in the logs on a clean shutdown
  def FinalDefaultGc: Rule1[List[GcEvent]] = rule {
    "Heap before GC invocations=" ~ SavedDigits ~ " (full " ~ Digits ~ "):\n" ~ nTimesMap(3, MemoryRegionSnapshot) ~
      IsoDate ~ ": " ~ Seconds ~ ": " ~ CollectionSummary
  } ~~> { (id, snap, from, _) =>
    MemoryRegions.ALL.flatMap { region =>
      GcSnapshot(id, TimeInterval(from, from), region, snap(region)) :: Nil
    }
  }

  // no timestamp: can't do anything
  def DefaultGcHeap: Rule1[List[GcEvent]] = rule {
    "Heap " ~ nTimesMap(3, MemoryRegionSnapshot)
  } ~~> { snaps => Nil }

  def CollectionSummary: Rule1[(Boolean, Option[Int], List[String], Duration)] = rule {
    "[ " ~ FullGc ~ " " ~ optional("(Allocation Failure) ") ~
      optional(SurvivorSummary) ~
      oneOrMore(CollectionRegionSummary) ~
      CollectionSizes ~
      optional(CollectionRegionSummary) ~ ", " ~ SavedSeconds ~
      " secs] " ~ optional(Times)
  } ~~> { (full, survivors, regions, permGen, duration) =>
    (full, survivors, regions ++ permGen, duration)
  }

  def FullGc: Rule1[Boolean] = rule { "GC--" | "GC" | "Full GC" } ~> { _ contains "Full" }

  def CollectionRegionSummary: Rule1[String] = rule {
    " [" ~ MemoryRegionName ~ ": " ~ CollectionSizes ~ "] "
  }
}

trait GcParserCms {
  this: GcParserCommon =>

  def CmsGc: Rule1[List[GcEvent]] = rule {
    ConcurrentSweep | ConcurrentMarks | ConcurrentSweepSnapshot
  }

  def ConcurrentMarks: Rule1[List[GcEvent]] = rule {
    oneOrMore(ConcurrentMark)
  } ~~> { _.flatten }

  def ConcurrentMark: Rule1[List[GcEvent]] = rule {
    optional(AbortedMark ~> identity) ~ IsoDate ~ ": " ~ SavedSeconds ~ ": [" ~
      optional("GC" ~ optional("[YG occupancy: " ~ Size ~ " (" ~ Size ~ ")]" ~ Seconds ~ ": [Rescan (parallel) , " ~ Seconds ~
        " secs]" ~ Seconds ~ ": [weak refs processing, " ~ Seconds ~ " secs] " ~ optional(Seconds ~
        ": [class unloading, " ~ Seconds ~ " secs]" ~ Seconds ~ ": [scrub symbol & string tables, " ~
        Seconds ~ " secs]")) ~ optional(" (" ~ oneOrMore(noneOf("\n)")) ~ ")") ~ " [1") ~
      " CMS-" ~ " " ~ oneOrMore(noneOf(" ]:")) ~> identity ~
      zeroOrMore(noneOf("\n")) ~ optional("\n")
  } ~~> { (aborted, date, seconds, name) =>
    GcMark(TimeInterval(date, date + seconds), name, aborted.isDefined) :: Nil
  }

  def AbortedMark: Rule0 = rule {
    " CMS: abort " ~ oneOrMore(!Digit)
  }

  def ConcurrentSweep: Rule1[List[GcEvent]] = rule {
    "Heap before GC invocations=" ~ SavedDigits ~ " (full " ~ Digits ~ "):\n" ~
      nTimesMap(3, MemoryRegionSnapshot) ~
      IsoDate ~ ": " ~ Seconds ~ ": " ~ CmsCollectionSummary ~
      "Heap after GC invocations=" ~ Digits ~ " (full " ~ Digits ~ "):\n" ~ nTimesMap(3, MemoryRegionSnapshot)
  } ~~> { (id, before, from, summary, after) =>
    val (maybeSurvivors, full, duration) = summary
    val to = from + duration
    val collectionsIn = (if (full) MemoryRegions.ALL else MemoryRegions.fromLogname("ParNew")).toSet

    MemoryRegions.ALL.flatMap { region =>
      if (collectionsIn(region))
        GcCollection(id, TimeInterval(from, to), region, before(region), after(region), full) :: Nil
      else
        GcSnapshot(id, TimeInterval(from, from), region, before(region)) ::
          GcSnapshot(id, TimeInterval(to, to), region, after(region)) :: Nil
    } ++ {
      maybeSurvivors match {
        case None => Nil
        case Some((thresh, survivors)) =>
          if (survivors.isEmpty) Nil
          else GcSurvivors(id, TimeInterval(to, to), thresh.get, survivors) :: Nil
      }
    }
  }

  def ConcurrentSweepSnapshot: Rule1[List[GcEvent]] = rule {
    "Heap before GC invocations=" ~ SavedDigits ~ " (full " ~ Digits ~ "):\n" ~
      nTimesMap(3, MemoryRegionSnapshot) ~
      IsoDate ~ ": " ~ Seconds ~ ": " ~ CmsCollectionSummary ~ optional(Seconds ~ "~ CMS")
  } ~~> { (id, before, from, summary) =>
    val (maybeSurvivors, _, duration) = summary
    val to = from + duration

    MemoryRegions.ALL.flatMap { region =>
      GcSnapshot(id, TimeInterval(from, from), region, before(region)) :: Nil
    } ++ {
      maybeSurvivors match {
        case None => Nil
        case Some((thresh, survivors)) =>
          if (survivors.isEmpty) Nil
          else GcSurvivors(id, TimeInterval(to, to), thresh.get, survivors) :: Nil
      }
    }
  }

  def CmsCollectionSummary: Rule1[(Option[(Option[Int], Map[Int, Long])], Boolean, Duration)] = rule {
    "[" ~ optional("Full" ~> identity) ~ " GC " ~ optional(IsoDate ~ ": ") ~ optional("(Allocation Failure) ") ~
      optional(Seconds ~ ": " ~ ParNewCollection) ~
      optional(Seconds ~ ": [CMS" ~ IsoDate ~ ": " ~ Seconds ~ ": [CMS" ~ oneOrMore("a" - "z" | anyOf(" -")) ~
        ": " ~ Seconds ~ "/" ~ Seconds ~ " secs]" ~ Times) ~
      optional(Seconds ~ ": [CMS: " ~ CollectionSizes ~ ", " ~ Seconds ~ " secs]") ~
      optional(" (concurrent mode failure): " ~ CollectionSizes ~ ", " ~ Seconds ~ " secs]") ~
      CollectionSizes ~
      optional(", [" ~ "CMS Perm" ~> identity ~ " : " ~ CollectionSizes ~ "]") ~
      optional(" icms_dc=" ~ Digits ~ " ") ~ ", " ~ SavedSeconds ~ " secs] " ~ optional(Times)
  } ~~> { (fullA, _, pn, _, fullB, duration) => (pn, fullA.isDefined || fullB.isDefined, duration) }

  def ParNewCollection: Rule1[(Option[Int], Map[Int, Long])] = rule {
    " [ParNew " ~
      // note: this mark is ignored (too awkward!)
      optional(ConcurrentMark ~ " ") ~
      optional("(promotion failed) ") ~
      optional(SurvivorSummary) ~ zeroOrMore(SurvivorsLineSummary) ~ ": " ~ CollectionSizes ~ ", " ~ Seconds ~
      " secs] "
  } ~~> { (mark, thresh, lines) => (thresh, lines.toMap) }

  def SurvivorsLineSummary: Rule1[(Int, Long)] = rule {
    "- age " ~ SavedDigits ~ ": " ~ SavedDigits ~ " bytes, " ~ Digits ~ " total "
  } ~~> { (age, bytes) => (age.toInt, bytes) }

}
