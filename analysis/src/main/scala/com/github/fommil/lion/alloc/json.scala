package com.github.fommil.lion.alloc

import spray.json._
import com.github.fommil.utils.TimeMarshalling

trait AllocationMarshalling {

  import DefaultJsonProtocol._
  import TimeMarshalling._

  // intern StackFrames as we load them to keep memory usage down
  implicit object StackFrameFormat extends RootJsonFormat[StackFrame] {
    val basic = jsonFormat4(StackFrame.apply)
    override def read(json: JsValue): StackFrame = basic.read(json).intern
    override def write(c: StackFrame): JsValue = basic.write(c)
  }

  implicit val AllocationSizesFormat = jsonFormat2(AllocationSizes)
  implicit val AllocationTracesFormat = jsonFormat2(AllocationTraces)
  implicit val AllocationLengthFormat = jsonFormat2(AllocationLength)
  implicit val AllocationLengthsFormat = jsonFormat2(AllocationLengths)

  implicit object AllocationSnapshotFormat extends RootJsonFormat[AllocationSnapshot] {
    override def read(json: JsValue): AllocationSnapshot = json match {
      case JsObject(f) => f("typeHint") match {
        case JsString("sizes") => AllocationSizesFormat.read(json)
        case JsString("traces") => AllocationTracesFormat.read(json)
        case JsString("lengths") => AllocationLengthsFormat.read(json)
        case _ => deserializationError("not valid")
      }
      case _ => deserializationError("not valid")
    }

    override def write(c: AllocationSnapshot): JsValue = c match {
      case s: AllocationSizes => AllocationSizesFormat.write(s)
      case t: AllocationTraces => AllocationTracesFormat.write(t)
      case l: AllocationLengths => AllocationLengthsFormat.write(l)
    }
  }

}

object AllocationMarshalling extends AllocationMarshalling