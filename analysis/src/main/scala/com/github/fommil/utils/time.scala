package com.github.fommil.utils

import scala.concurrent.duration.{FiniteDuration, Duration, DurationLong}
import java.text.{SimpleDateFormat, DateFormat}

/*
 * This file is a bit of an embarrassment... at the time of writing there
 * is no de-facto standard Scala timestamp / interval libraries, so this
 * contains case classes that feel like they should be in a stdlib. Sorry!
 * I would have used Joda, but DateTime doesn't implement `equals`, making
 * it inappropriate for use in pattern matching.
 */


case class Timestamp(instant: Long) extends Ordered[Timestamp] {
  def +(d: Duration) = Timestamp(instant + d.toMillis)
  def -(d: Duration) = Timestamp(instant - d.toMillis)
  def +(that: Timestamp) = Timestamp(instant + that.instant)
  def -(that: Timestamp) = Timestamp(instant - that.instant)
  override def compare(that: Timestamp) = instant compare that.instant
}
object Timestamp extends (Long => Timestamp) {
  private val parser = new ThreadLocal[DateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
  }
  def parse(iso: String): Timestamp = Timestamp(parser.get.parse(iso).getTime)
}

// [from, to) (contains 'to' iff from == to)
case class TimeInterval(from: Timestamp, to: Timestamp) extends Ordered[TimeInterval] {
  require(to >= from)
  override def compare(that: TimeInterval) =
    implicitly[Ordering[(Timestamp, Timestamp)]].compare((from, to), (that.from, that.to))
  def duration: Duration = (to - from).instant.millis
  def mid: Timestamp = Timestamp(from.instant / 2 + to.instant / 2)
  def contains(t: Timestamp) = t == from || (t >= from && t < to)
  def overlaps(that: TimeInterval) =
    (from <= that.from && that.from < to) ||
      (from < that.to && that.to <= to) ||
      (that.from < from && to < that.to)
}
object TimeInterval extends ((Timestamp, Timestamp) => TimeInterval) {
  def apply(from: Long, to: Long): TimeInterval = TimeInterval(Timestamp(from), Timestamp(to))
}

// the last entry may be smaller than the rest
class TimeIntervalRange private (start: Timestamp, end: Timestamp, s: Long,
                                 override val length: Int) extends IndexedSeq[TimeInterval] {
  override def apply(idx: Int): TimeInterval = {
    require(idx >= 0 && idx < length)
    val from = Timestamp(start.instant + idx * s)
    val to = if (idx == length - 1) end else Timestamp(start.instant + (idx + 1) * s)
    TimeInterval(from, to)
  }
}

object TimeIntervalRange {
  def apply(start: Timestamp, end: Timestamp, step: FiniteDuration): TimeIntervalRange = {
    require(end > start)
    val s = step.toMillis
    val length = {
      val diff = end.instant - start.instant
      val div = diff / s
      val rem = diff % s
      if (rem == 0) div else div + 1
    }
    require(length > 0)
    new TimeIntervalRange(start, end, s, length.toInt)
  }
}
