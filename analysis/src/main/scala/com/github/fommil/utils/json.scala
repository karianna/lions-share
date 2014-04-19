package com.github.fommil.utils

import spray.json.{JsonWriter, JsValue}
import java.io.Writer

trait JsSupport {
  protected implicit def toJsValue[T](a: T)(implicit w: JsonWriter[T]) = w.write(a)

  protected def jsVar(name: String, value: JsValue)(implicit w: Writer) {
    w.append(s"var $name = ")
    w.append(value.compactPrint)
    w.append(";\n")
  }
}