package com.github.fommil.utils

import scala.io.Source
import java.util.zip.GZIPInputStream

trait GzResourceSupport {
  protected def fromGzRes[T](res: String) = {
    val source = Source.fromInputStream(new GZIPInputStream(getClass.getResourceAsStream(res)))
    try {
      source.getLines().mkString("\n")
    } finally source.close()
  }
}
