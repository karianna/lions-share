package com.github.fommil.lion.alloc

import com.github.fommil.utils.{OrderedByInterval, TimeInterval, Pimps}
import scala.collection.immutable.ListMap
import com.google.common.cache.CacheBuilder
import Pimps.closureToCallable
import StackFrame.{interned, clean}

sealed trait AllocationSnapshot extends OrderedByInterval[AllocationSnapshot]

case class AllocationSizes(interval: TimeInterval,
                           sizes: Map[Clazz, Long]) extends AllocationSnapshot {
  def trim(keep: Int) = copy(sizes = sizes.toList.sortBy(-_._2).take(keep).toMap)
}

case class AllocationTraces(interval: TimeInterval,
                            traces: Map[Clazz, List[StackTrace]]) extends AllocationSnapshot

case class AllocationLengths(interval: TimeInterval,
                             lengths: Map[Clazz, List[AllocationLength]]) extends AllocationSnapshot

case class AllocationLength(length: Int, count: Long)

case class StackFrame(clazz: String, method: String, file: Option[String], line: Option[Int]) {
  def intern = interned.get(this, this)

  def cleanName = {
    val cleanedMethod = clean(method)
    clean(clazz) + (if (cleanedMethod == "apply") "" else "." + cleanedMethod) +
      " (" + file.getOrElse("?") + ":" + line.fold("?")(_.toString) + ")"
  }
}

object StackFrame {
  private def clean(name: String) =
    (name /: candy)((r, c) => r.replace(c._1, c._2)).replaceAll("\\$\\d+", "")

  private val candy = ListMap(
    // manual
    "$$" -> "$",
    "$next" -> "",
    "$apply" -> "",
    "$mcV" -> "",
    "$mcI" -> "",
    "$query" -> "",
    "$.init" -> "",
    "$default" -> "",
    // https://github.com/scala/scala/blob/master/src/library/scala/reflect/NameTransformer.scala
    "$tilde" -> "~",
    "$eq" -> "=",
    "$less" -> "<",
    "$greater" -> ">",
    "$bang" -> "!",
    "$hash" -> "#",
    "$percent" -> "%",
    "$up" -> "^",
    "$amp" -> "&",
    "$bar" -> "|",
    "$times" -> "*",
    "$div" -> "/",
    "$plus" -> "+",
    "$minus" -> "-",
    "$colon" -> ":",
    "$bslash" -> "\\",
    "$qmark" -> "?",
    "$at" -> "@",
    // https://github.com/scala/scala/blob/master/src/reflect/scala/reflect/internal/StdNames.scala
    "$anonfun" -> "",
    "$anon" -> "",
    "$class" -> "",
    "$module" -> "",
    "$sp" -> ""
  )

  // interning is sometimes needed... these are incredibly repetitive
  protected val interned = CacheBuilder.newBuilder.weakKeys.weakValues.build[StackFrame, StackFrame]

  def apply(e: StackTraceElement): StackFrame =
    apply(e.getClassName, e.getMethodName, Option(e.getFileName), Option(e.getLineNumber))
}
