package com.github.fommil.utils

import scala.io.Source
import java.util.zip.GZIPInputStream
import java.io.{FileWriter, FileOutputStream, File}

trait StringGzResourceSupport {
  protected def fromGzRes[T](res: String) = {
    val source = Source.fromInputStream(new GZIPInputStream(getClass.getResourceAsStream(res)))
    try source.getLines().mkString("\n")
    finally source.close()
  }
}

trait StringResourceSupport {
  protected def fromRes[T](res: String) = {
    val source = Source.fromInputStream(getClass.getResourceAsStream(res))
    try source.getLines().mkString("\n")
    finally source.close()
  }
}

trait StringFileSupport {
  protected def fromFile(file: File) = {
    val source = Source.fromFile(file)
    try source.getLines().mkString("\n")
    finally source.close()
  }

  protected def toFile(file: File, text: String): Unit = {
    val writer = new FileWriter(file)
    try writer.write(text)
    finally writer.close()
  }
}
