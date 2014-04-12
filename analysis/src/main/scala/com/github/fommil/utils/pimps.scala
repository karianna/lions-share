package com.github.fommil.utils

class PimpedAny[T](val a: T) extends AnyVal {
  def withEffect(effect: T => Unit) = {
    effect(a)
    a
  }
}

object PimpedAny {
  implicit def withEffectAny[T](a: T) = new PimpedAny(a)
}