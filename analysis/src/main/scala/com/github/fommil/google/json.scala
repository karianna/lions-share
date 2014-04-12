package com.github.fommil.google

import spray.json._

trait DataTableMarshalling {
  import DefaultJsonProtocol._
  implicit val DataCellFormat = jsonFormat2(DataCell)

  private object Special extends DefaultJsonProtocol with NullOptions {
    val LabelCellFormatWithNulls = jsonFormat1(LabelCell)
  }
  implicit val LabelCellFormat = Special.LabelCellFormatWithNulls

  implicit object CellFormat extends RootJsonFormat[Cell] {
    override def read(json: JsValue): Cell = json match {
      case JsObject(f) => f("v") match {
        case s: JsString => LabelCellFormat.read(json)
        case n: JsNumber => DataCellFormat.read(json)
        case _ => deserializationError("not valid")
      }
      case _ => deserializationError("not valid")
    }
    override def write(c: Cell): JsValue = c match {
      case d: DataCell => DataCellFormat.write(d)
      case l: LabelCell => LabelCellFormat.write(l)
    }
  }

  implicit val HeaderFormat = jsonFormat4(Header)
  implicit val RowFormat = jsonFormat1(Row)
  implicit val DataTableFormat = jsonFormat2(DataTable)
}

object DataTableMarshalling extends DataTableMarshalling