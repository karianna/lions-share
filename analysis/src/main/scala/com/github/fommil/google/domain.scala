package com.github.fommil.google

import scala.collection.immutable.ListSet
import LabelCell.NullCell

case class Header(label: Option[String],
                  `type`: String,
                  id: Option[String],
                  role: Option[String])

object DataHeader {
  def apply(label: String, `type`: String = "number", id: Option[String] = None) =
    Header(Some(label), `type`, id, None)
}

object RoleHeader {
  def apply(role: String, `type`: String = "string", id: Option[String] = None, label:  Option[String] = None) =
    Header(label, `type`, id, Some(role))
}

sealed trait Cell
case class DataCell(v: BigDecimal, f: Option[String] = None) extends Cell
case class LabelCell(v: String) extends Cell
object LabelCell extends (String => LabelCell) {
  val NullCell = LabelCell(null)
}

case class Row(c: Seq[Cell]) {
  def head = c.head
}

case class DataTable(cols: Seq[Header], rows: Seq[Row]) {
  def rowStarting(key: Cell): Option[Row] = rows.find(_.head == key)

  /**
   * Create an inner join with the other table. Joins on the first column.
   *
   * @param strict true to exclude entries that do not appear in both.
   *               If false, mismatched rows will appear with `null`s
   *               for the columns that have no values.
   */
  def join(other: DataTable, strict: Boolean = true): DataTable = {
    val headers = cols ++ other.cols.drop(1)
    val body = rows.flatMap { r =>
      other.rowStarting(r.head).map { row => Row(r.c ++ row.c.drop(1)) }
    } ++ {
      if (strict) Nil
      else {
        // order is important
        val leftKeys = ListSet.empty ++ rows.map { _.c(0) }
        val rightKeys = ListSet.empty ++ other.rows.map { _.c(0) }
        ((leftKeys -- rightKeys).map { key =>
          Row(key +: rowStarting(key).get.c.drop(1) ++: List.fill(other.cols.size - 1)(NullCell))
        } ++ (rightKeys -- leftKeys).map { key =>
          Row(key +: List.fill(cols.size - 1)(NullCell) ++: other.rowStarting(key).get.c.drop(1))
        }).toList.reverse
      }
    }

    DataTable(headers, body)
  }

  private def idxFor(name: String) = {
    val idx = cols.indexWhere {
      case Header(Some(`name`), _, _, _) => true
      case _ => false
    }
    require(idx >= 0, name + " not found. I have: " + cols)
    idx
  }

  def dropColumn(name: String): DataTable = {
    val idx = idxFor(name)
    val header = cols.splitAt(idx) match {
      case (left, right) => left ++ right.tail
    }
    val body = rows.map { row =>
      Row(
        row.c.splitAt(idx) match {
          case (left, right) => left ++ right.tail
        })
    }
    DataTable(header, body)
  }

  def ascending(name: String): DataTable = {
    val idx = idxFor(name)
    val body = rows.sortBy(_.c(idx) match {
      case DataCell(v, _) => v
      case _ => ???
    })
    DataTable(cols, body)
  }

}

