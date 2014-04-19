package com.github.fommil.lion.alloc

import spray.json.pimpString
import collection.breakOut

// the JSON that is produced by the agent is not technically JSON
// because it depends on whitespace to separate elements
object AllocationParser {
  import AllocationMarshalling._

  def parse(source: String): List[AllocationSnapshot] =
    source.split("\n").par.map(_.parseJson.convertTo[AllocationSnapshot]).toList.sorted
}
