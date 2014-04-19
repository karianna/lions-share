package com.github.fommil.lion.gc

import com.github.fommil.utils.{OrderedByInterval, TimeInterval}

trait GcEvent extends OrderedByInterval[GcEvent] {
  def groupId: Long
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
    case ("Metaspace" | "PSPermGen" | "concurrent-mark-sweep perm gen", _) => Perm
  }

  def fromLogname(region: String): Set[MemoryRegion] = region match {
    case "PSYoungGen" | "par new generation" | "ParNew" => Set(Eden, From, To)
    case "ParOldGen" | "PSOldGen" | "concurrent mark-sweep generation" => Set(Tenured)
    case "Metaspace" | "PSPermGen" | "concurrent-mark-sweep perm gen" => Set(Perm)
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
