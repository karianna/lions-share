package com.github.fommil.lion.alloc

import akka.event.slf4j.SLF4JLogging
import com.github.fommil.google._
import LabelCell.NullCell
import com.github.fommil.utils.Pimps
import scala.util.Random

object AllocationAnalyser {
  // collapses traces that are either in or out of the standard library
  val StdLib: (String => Boolean) = name => name.matches("""^(java|scala).*""")
}

class AllocationAnalyser extends SLF4JLogging {

  // these combined methods are a bit rubbish
  // we could really do with variation analysis like in GcAnalysis
  def combinedAllocationSizes(all: Seq[AllocationSnapshots]): DataTable =
    allocationSizes(all.reduce(_ ++ _))

  def combinedAllocationLengths(all: Seq[AllocationSnapshots]) =
    allocationLengths(all.reduce(_ ++ _))

  def combinedAllocationTraces(all: Seq[AllocationSnapshots])
                              (filter: String => Boolean) =
    allocationTraces(all.reduce(_ ++ _))(filter)

  def allocationSizes(all: AllocationSnapshots): DataTable = {
    val allocs = all.collect { case a: AllocationSizes => a }
    val clazzes = {
      for {
        alloc <- allocs
        count <- alloc.sizes
        clazz = count._1
      } yield clazz
    }.distinct.toList

    val start = all.min.interval.from

    val header = DataHeader("DateTime") :: clazzes.map(DataHeader(_))
    val body = for (alloc <- allocs) yield Row({
      DataCell((alloc.interval.to - start).instant) +: clazzes.map { clazz =>
        alloc.sizes.find(_._1 == clazz) match {
          case Some((_, size)) => DataCell(size / (1024 * 1024.0))
          case _ => NullCell
        }
      }
    })
    DataTable(header, body)
  }

  def allocationTraces(all: AllocationSnapshots)
                      (filter: String => Boolean): Map[Clazz, Map[String, List[Node]]] = {
    val allocs = all.collect { case a: AllocationTraces => a }
    val start = all.min.interval.from

    def toNodes(trace: StackTrace): List[Node] = trace match {
      case Nil => Nil
      case head :: tail => Node(toNodes(tail), head.cleanName, 1) :: Nil
    }

    val nodes = for {
      (time, sources) <- allocs.groupBy(_.interval.to).toList
      source <- sources
      (clazz, traces) <- source.traces
      trace <- traces
      node <- toNodes(trace.reverse)
    } yield (clazz, time, node)

    def squash(nodes: List[Node]): List[Node] = if (nodes.isEmpty) Nil
    else (List.empty[Node] /: nodes) { case (r, n) =>
      r.find(_.name == n.name) match {
        case None => n :: r
        case Some(found) =>
          def merge(a: Node, b: Node): Node = a.copy(
            children = squash(a.children ++ b.children),
            size = a.size + b.size)
          merge(n, found) :: r.filterNot(_ == found)
      }
    }

    def clean(nodes: List[Node]): List[Node] = {
      def collapse(node: Node): Node = node.children match {
        case Nil => node
        case child :: Nil if filter(node.name) != filter(child.name) =>
          node.copy(children = List(collapse(child)))
        case child :: Nil =>
          val newChild = collapse(child)
          val combined = node.name + "<br />" + newChild.name
          Node(newChild.children, combined, node.size)
        case children =>
          node.copy(children = node.children.map(collapse))
      }
      nodes map collapse
    }

    // Seq[(A,B,C)] => Map[A, Map[B, Seq[C]]]
    val tree = nodes.groupBy(_._1).mapValues(_.groupBy(_._2).mapValues(_.map(_._3)))

    tree.mapValues(_.map {
      // Map keys must be Strings in JSON
      case (k, v) => ((k - start).instant.toString, clean(squash(v)).sortBy(-_.size))
    })
  }

  def allocationLengths(all: AllocationSnapshots) = {
    val allocs = all.collect { case a: AllocationLengths => a }
    import Pimps._
    val data = {
      for {
        alloc <- allocs
        lengths <- alloc.lengths
      } yield lengths
    }.toMultiMap.mapValues { v =>
      (for (t <- v; b <- t) yield (b.length, b.count)).toMultiMap.mapValues(_.sum)
    }

    val clazzes = data.keys.toList
    val header = DataHeader("Array Length") :: clazzes.map {
      DataHeader(_)
    }

    val body = {
      for {
        (clazz, h) <- data
        (length, count) <- h
      } yield {
        val idx = clazzes.indexOf(clazz) + 1
        Row(DataCell(length.toLong) :: (1 until idx).toList.map(v => NullCell) :::
          DataCell(count) :: (idx until clazzes.size).toList.map(v => NullCell))
      }
    }.toList

    DataTable(header, body)
  }

}


case class Node(children: List[Node], name: String, size: Int, id: Int = Random.nextInt())
