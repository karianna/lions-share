package com.github.fommil.utils

import scala.collection.{SortedMap, breakOut}
import java.util.concurrent.Callable

object Pimps {

  // nice way to perform side-effects, if you must...
  implicit class PimpedAny[T](a: T) {
    def withEffect(effect: T => Unit) = {
      effect(a)
      a
    }
  }

  // poor man's MultiMap
  implicit class PimpedAsMultimap[K, V](a: Traversable[(K, V)]) {
    def toMultiMap: Map[K, Seq[V]] = a.groupBy(_._1).map {
      case (k, vs) => (k, vs.map(_._2)(breakOut))
    }

    def toSortedMultiMap(implicit s: Ordering[K]): SortedMap[K, Seq[V]] = a.groupBy(_._1).map {
      case (k, vs) => (k, vs.map(_._2)(breakOut))
    }(breakOut)
  }

  // interact with legacy Java APIs
  implicit def closureToCallable[T](t: => T): Callable[T] = new Callable[T] {
    def call: T = t
  }

}