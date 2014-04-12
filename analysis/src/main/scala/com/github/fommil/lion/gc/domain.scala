package com.github.fommil.lion.gc

import scala.concurrent.duration.{Duration, DurationLong}
import java.text.{SimpleDateFormat, DateFormat}

// there is no de-facto standard case class for moments in time
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

case class TimeInterval(from: Timestamp, to: Timestamp) extends Ordered[TimeInterval] {
  def duration: Duration = (to - from).instant.millis
  override def compare(that: TimeInterval) = (from, to) compare(that.from, that.to)
}
object TimeInterval extends ((Timestamp, Timestamp) => TimeInterval) {
  def apply(from: Long, to: Long): TimeInterval = TimeInterval(Timestamp(from), Timestamp(to))
}

trait GcEvent extends Ordered[GcEvent] {
  def groupId: Long
  def interval: TimeInterval
  override def compare(that: GcEvent) = interval compare that.interval
}

case class MemoryUsage(limit: Long, usedPercentage: Double) {
  require(limit > 0, limit)
  require(usedPercentage >= 0 && usedPercentage <= 1, usedBytes)

  def usedBytes = (limit * usedPercentage).toLong
}

case class MemoryRegion(name: String)

case object MemoryRegion {
  val Eden = MemoryRegion("Eden")
  val From = MemoryRegion("Survivor from")
  val To = MemoryRegion("Survivor to")
  val Tenured = MemoryRegion("Tenured")
  val Perm = MemoryRegion("Perm")
}

object MemoryRegions {

  import MemoryRegion._

  def fromLognames(region: String, sub: String): MemoryRegion = (region, sub) match {
    case ("PSYoungGen" | "par new generation", "eden") => Eden
    case ("PSYoungGen" | "par new generation", "from") => From
    case ("PSYoungGen" | "par new generation", "to") => To
    case ("ParOldGen" | "PSOldGen" | "concurrent mark-sweep generation", _) => Tenured
    case ("PSPermGen" | "concurrent-mark-sweep perm gen", _) => Perm
  }

  def fromLogname(region: String): Set[MemoryRegion] = region match {
    case "PSYoungGen" | "par new generation" | "ParNew" => Set(Eden, From, To)
    case "ParOldGen" | "PSOldGen" | "concurrent mark-sweep generation" => Set(Tenured)
    case "PSPermGen" | "concurrent-mark-sweep perm gen" => Set(Perm)
  }

  val ALL = Eden :: From :: To :: Tenured :: Perm :: Nil

  def isNewGen(region: MemoryRegion) = region match {
    case Eden | From | To => true
    case _ => false
  }
}

case class GcCollection(groupId: Long,
                        interval: TimeInterval,
                        region: MemoryRegion,
                        before: MemoryUsage,
                        after: MemoryUsage,
                        full: Boolean) extends GcEvent

// groupId must be negative if this is user-generated (i.e. not parsed from a gc log)
case class GcSnapshot(groupId: Long,
                      interval: TimeInterval,
                      region: MemoryRegion,
                      current: MemoryUsage) extends GcEvent

case class GcMark(interval: TimeInterval,
                  phase: String,
                  aborted: Boolean) extends GcEvent {
  def groupId = Long.MaxValue
}

case class GcSurvivors(groupId: Long,
                       interval: TimeInterval,
                       threshold: Int,
                       distribution: Map[Int, Long] = Map.empty) extends GcEvent
