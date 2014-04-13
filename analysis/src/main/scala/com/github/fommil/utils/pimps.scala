package com.github.fommil.utils

import scala.collection.{SortedMap, breakOut}

// nice way to perform side-effects, if you must...
class PimpedAny[T](val a: T) extends AnyVal {
  def withEffect(effect: T => Unit) = {
    effect(a)
    a
  }
}
object PimpedAny {
  implicit def withEffectAny[T](a: T) = new PimpedAny(a)
}


// poor man's MultiMap
class PimpedAsMultimap[K, V](val a: Traversable[(K, V)]) extends AnyVal {
  def toMultiMap: Map[K, Seq[V]] = a.groupBy(_._1).map {
    case (k, vs) => (k, vs.map(_._2)(breakOut))
  }

  def toSortedMultiMap(implicit s: Ordering[K]): SortedMap[K, Seq[V]] = a.groupBy(_._1).map {
    case (k, vs) => (k, vs.map(_._2)(breakOut))
  }(breakOut)
}
object PimpedAsMultimap {
  implicit def pimpAsMultimap[K, V](a: Traversable[(K, V)]) = new PimpedAsMultimap(a)
}
