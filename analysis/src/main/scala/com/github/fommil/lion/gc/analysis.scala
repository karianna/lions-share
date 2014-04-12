package com.github.fommil.lion.gc

import com.github.fommil.utils.{PimpedAny, TimeInterval, TimeIntervalRange, Timestamp}
import com.github.fommil.google._

import scala.concurrent.duration
import duration._
import scala.Some
import com.github.fommil.google.DataTable
import com.github.fommil.google.Row
import java.util.concurrent.TimeUnit
import PimpedAny._
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import LabelCell.NullCell
import FiniteDuration.FiniteDurationIsOrdered

/** Produces data for consumption by Google Charts from garbage collection logs.
  * Methods taking a single `List[GcEvent]` are for analysing a single process.
  * Methods taking multiple `List[GcEvent]`s are for statistical analysis over
  * repeated runs of the same process.
  *
  * NOTE: JavaScript must be used on the client side to convert the
  * millisecond dates into `Date` format (this is not possible via JSON).
  */
class GcAnalyser {
  protected case class HeapInstant(timestamp: Timestamp, used: Long, collected: Long) extends Ordered[HeapInstant] {
    override def compare(that: HeapInstant): Int = timestamp compare that.timestamp
  }

  /** A time-series of memory allocations, timestamps are accurate only to
    * the nearest subsequent garbage collection. Table takes the form:
    * ```
    * DateTime    | Allocated
    * -----------------------
    * (UNIX time) | (Bytes)
    * ...
    * ```
    * suitable for a Google Charts Scatter.
    */
  def allocations(events: GcEvents): DataTable = {
    val raw = allocationsRaw(events)
    val base = events.min.interval.from
    val baselined = raw.map { case (time, v) => ((time.instant - base.instant).millis, v) }
    val binned = timeseriesBins(baselined, 10 seconds, base)

    val headers = DataHeader("Timestamp") :: DataHeader("Allocated") :: Nil
    val body = binned.map { case (interval, allocated) =>
      Row(TimeCell(interval.to) :: DataCell(allocated) :: Nil)
    }
    DataTable(headers, body)
  }

  /** A time-series of allocation rates averaged over several processes.
    * Table takes the form:
    * ```
    * DateTime     | Average     | Min | -VE | +VE | Max
    * ---------------------------------------------------
    * (UNIX time)  | (Bytes/sec) | ... | ... | ... | ....
    * ```
    * This format is appropriate for use in a Google Chart Intervals.
    */
  def averageAllocations(processes: Seq[GcEvents]) = {
    timeseriesQuantiles(baseline(processes map allocationsRaw))
  }

  // takes a list of discrete timeseries datums and bins them by the step size,
  // returning the intervals and the frequencies of the data (if normalised) or
  // the averages
  protected def timeseriesBins(allocations: Seq[(Duration, Double)],
                               step: FiniteDuration,
                               start: Timestamp = Timestamp(0),
                               normalise: Boolean = true) = {
    require(allocations.nonEmpty)

    val n = step.toUnit(TimeUnit.SECONDS)
    // Timestamps are misused here for relative - not UNIX - time
    val allocs = allocations.map(t => (Timestamp(t._1.toMillis), t._2))
    val (min, max) = (Timestamp(0), allocs.map(_._1).max)
    TimeIntervalRange(min, max, step).flatMap { interval =>
      allocs.collect {
        case (t, a) if interval contains t => a
      } match {
        case found if found.isEmpty => None
        case found =>
          val shiftedStart = start + interval.from
          val shiftedEnd = start + interval.to
          val shifted = TimeInterval(shiftedStart, shiftedEnd)
          val value = if (normalise) found.sum / n else found.sum / found.size
          Some((shifted, value ))
      }
    }
  }

  // takes multiple streams of timeseries Long data, bins each stream
  // and produces Google Chart data for inclusion in an Intervals chart using the given
  // function for computing the intervals)
  protected def timeseriesQuantiles(data: Seq[Seq[(Duration, Double)]],
                                    label: String = "Median",
                                    iLabels: List[String] = List.fill(4)("i0"),
                                    percentiles: List[Double] = List(Double.MinPositiveValue, 20, 80, 100),
                                    normalise: Boolean = true) = {
    val headers = DataHeader("Seconds") :: DataHeader(label) ::
      iLabels.map(i => RoleHeader("interval", `type` = "number", id = Some(i)))

    val body = data.flatMap { raw => timeseriesBins(raw, 10 seconds, normalise = normalise) }.
      groupBy(_._1).map(kv => (kv._1, kv._2.map(_._2))).toList.sortBy(_._1). // poor man's TreeMultiMap
      flatMap {
      case (interval, rates) if rates.isEmpty => None
      case (interval, rates) =>
        val p = new Percentile withEffect { _.setData(rates.toArray) }
        Some(Row((interval.mid.instant / 1000.0 :: p.evaluate(50) :: percentiles.map { p.evaluate }).map { DataCell(_) }))
    }.toList
    DataTable(headers, body)
  }

  protected def baseline[T](processes: Seq[Seq[(Timestamp, T)]]) = processes.map { data =>
    val base = data.unzip._1.min.instant
    data.map { case (time, value) => ((time.instant - base).millis, value) }
  }

  protected def allocationsRaw(events: GcEvents) = {
    // anything part of a GC collection will be atomic and share the same timestamps
    val loggedInstants = events.collect {
      case GcCollection(_, TimeInterval(from, to), _, before, after, _) =>
        HeapInstant(from, before.usedBytes, 0L) ::
          HeapInstant(to, after.usedBytes, before.usedBytes - after.usedBytes) :: Nil
      case GcSnapshot(id, time, _, usage) if id >= 0 =>
        HeapInstant(time.from, usage.usedBytes, 0) :: Nil
    }.flatten.groupBy(_.timestamp).values.map { grouped =>
      grouped.reduce((a, b) => HeapInstant(a.timestamp, a.used + b.used, a.collected + b.collected))
    }

    // For some GCs, we may have snapshots while garbage is collected concurrently.
    // This is unfortunate and would result in a noisy aggregate allocation
    // estimate during the GC. We could bypass the issue by ignoring all non-`gc.log`
    // snapshots that were taken while a garbage collection was in progress.
    val gcBlocks = events.collect {
      case GcCollection(_, interval, _, _, _, _) => interval
    }.toSet
    def blacklisted(test: TimeInterval) = gcBlocks.exists { _.overlaps(test) }
    // obtained by JMX and user managed, rather than parsed
    val userInstants = events.filter(_.groupId < 0).groupBy(_.groupId).values.flatMap { group =>
    // timestamps of the JMX polls may differ across regions
      val start = group.minBy(_.interval).interval.from
      val end = group.maxBy(_.interval).interval.to
      if (blacklisted(TimeInterval(start, end))) None
      else {
        group.collect {
          case GcSnapshot(_, time, _, usage) => HeapInstant(time.from, usage.usedBytes, 0)
        }
      }.reduceOption { (a, b) => HeapInstant(end, a.used + b.used, 0) }
    }

    val instants = (loggedInstants ++ userInstants).toSeq.sorted

    instants.sliding(2).map { case Seq(last, now) =>
      (now.timestamp, (now.used + now.collected - last.used).toDouble / 1024 / 1024 / 1024)
    }.toList
  }


  /** A time-series of garbage collection pause times by concurrent
    * and "stop the world" category. Table takes the form:
    * ```
    * DateTime     | Pauses
    * --------------------------
    * (UNIX time)  | (Seconds)
    * ...
    * ```
    * This format is appropriate for use in a Google Chart Scatter.
    */
  def pauses(events: GcEvents): DataTable = {
    val headers = List("DateTime", "NewGen", "Full GC").map { DataHeader(_) }
    val body = events.collect {
      case GcCollection(_, interval@TimeInterval(from, _), _, _, _, false) =>
        Row(List(TimeCell(from), DataCell(interval.duration.toMillis), NullCell))
      case GcCollection(_, interval@TimeInterval(from, _), _, _, _, true) =>
        Row(List(TimeCell(from), NullCell, DataCell(interval.duration.toMillis)))
    }
    DataTable(headers, body)
  }

  /** A time-series of garbage collection pause times by concurrent
    * and "stop the world" category. Table takes the form:
    * ```
    * DateTime     | Average     | Min | -VE | +VE | Max | Full Min | Full Max
    * ------------------------------------------------------------------------
    * (UNIX time)  | (Bytes/sec) | ... | ... | ... | ... | ...      | ...
    * ...
    * ```
    * This format is appropriate for use in a Google Chart Interval.
    */
  def averagePauses(processes: Seq[GcEvents]): DataTable = {
    def duration(from: Timestamp, to: Timestamp) = (to.instant - from.instant) / 1000.0

    def collectPauses(full: Boolean) = processes.map { process =>
      val start = process.min.interval.from
      process.collect {
        case GcCollection(_, TimeInterval(from, to), _, _, _, `full`) =>
          ((from.instant - start.instant).millis, duration(from, to))
      }.sortBy(_._1)
    }

    val newgen = collectPauses(full = false)
    val oldgen = collectPauses(full = true)

    val fast = timeseriesQuantiles(newgen, "NewGen")
    val slow = timeseriesQuantiles(oldgen, "Full",
      iLabels = List("i1", "i1"),
      percentiles = List(Double.MinPositiveValue, 100))

    fast.join(slow, strict = false).dropColumn("Full").ascending("Seconds")
  }

  /** Histogram data of throughput (percentage time not in GC)
    * ```
    * ID        | Throughput (%)
    * ------------------------------------------------------------------------------------------
    * (string)  | [0, 100]
    * ...
    * ```
    * This format is appropriate for use in a Google Chart Combo, using Stacked Areas
    * for the memory regions and lines for the before/after limits.
    *
    */
  def throughput(processes: Map[String, GcEvents], title: String = "Throughput"): DataTable = {
    def uptime(events: GcEvents) = (events.max.interval.to.instant - events.min.interval.from.instant) / 1000.0
    def collecting(events: GcEvents) = events.collect { case c: GcCollection =>
      (c.groupId, c.interval.duration)
    }.toMap.values.reduceOption[Duration](_ + _).getOrElse(Duration.Zero).toUnit(TimeUnit.SECONDS)

    val headers = DataHeader("ID") :: DataHeader(title) :: Nil
    val body = processes.map { case (id, events) =>
      val up = uptime(events)
      val gc = collecting(events)
      Row(LabelCell(id) :: DataCell(100 * (up - gc) / up) :: Nil)
    }.toList
    DataTable(headers, body)
  }

  // do a specialist one
  def profile(events: GcEvents) = averageProfile(Seq(events))

  /** A stacked interval time-series of heap usage before and after GC (with quantiles).
    * Table takes the form (O = Old Gen, N = New Gen + Old Gen):
    * ```
    * DateTime    | OAfter | OMin | O-VE | O+VE | OMax | (repeat: NBefore, NAfter)
    * -----------------------------------------------------------------------------------------
    * (UNIX time) | (Bytes) ...
    * ```
    * This format is appropriate for use in a Google Chart Interval.
    */
  def averageProfile(processes: Seq[GcEvents]): DataTable = {
    val gb = 1024.0 * 1024 * 1024

    // DateTime (when GC finished), (OAfter, NAfter, NBefore)
    def extractSizes(collections: Iterable[GcEvent]): (Timestamp, (Long, Long, Long)) = {
      val from = collections.head.interval.from
      val to = collections.head.interval.to

      import MemoryRegion.Tenured
      val oAfter = collections.collect  {
        case GcSnapshot(_, TimeInterval(time, _), Tenured, snap) if time != from => snap.usedBytes
        case GcCollection(_, _, Tenured, _, after, _) => after.usedBytes
      }.sum
      val nBefore = collections.collect {
        case GcSnapshot(_, TimeInterval(time, _), r, snap) if MemoryRegions.isNewGen(r) & time != from => snap.usedBytes
        case c: GcCollection if MemoryRegions.isNewGen(c.region) => c.before.usedBytes
      }.sum
      val nAfter = collections.collect {
        case GcSnapshot(_, TimeInterval(time, _), r, snap) if MemoryRegions.isNewGen(r) & time != to => snap.usedBytes
        case c: GcCollection if MemoryRegions.isNewGen(c.region) => c.after.usedBytes
      }.sum
      (collections.head.interval.to, (oAfter, nBefore, nAfter))
    }

    val sizes = baseline(processes.map {
      _.groupBy(_.groupId).values.filter(_.count(_.isInstanceOf[GcCollection]) > 0).map { extractSizes }.toSeq
    })

    val oAfter = sizes.map { _.map { v => (v._1, v._2._1.toDouble / gb) } }
    val nBefore = sizes.map { _.map { v => (v._1, v._2._2.toDouble / gb) } }
    val nAfter = sizes.map { _.map { v => (v._1, v._2._3.toDouble / gb) } }

    val tight = List(Double.MinPositiveValue, 40.0, 60.0, 100.0)
    val oa = timeseriesQuantiles(oAfter, label = "OldGen",
      iLabels = List("oa2", "oa1", "oa1", "oa2"), percentiles = tight, normalise = false)
    val nb = timeseriesQuantiles(nBefore, label = "NewGen Before GC",
      iLabels = List("nb2","nb1","nb1","nb2"), percentiles = tight, normalise = false)
    val na = timeseriesQuantiles(nAfter, label = "NewGen After GC",
      iLabels = List("na2","na1","na1","na2"), percentiles = tight, normalise = false)

    oa.join(nb.join(na, strict = false), strict = false)
  }

}
