package com.github.fommil.google

import scala.collection.immutable.NumericRange

// raw samples with an id, double value and label associated to each sample
case class Samples(ids: Seq[String], data: Seq[Double], labels: Seq[String]) {
  require(ids.size == labels.size)
  require(data.size == labels.size)

  def add(id: String, datum: Double, label: String) = {
    Samples(ids :+ id, data :+ datum, labels :+ label)
  }
}

object Samples {
  def apply(data: Seq[Double]): Samples = {
    val ids = (1 to data.size).map{_.toString}
    Samples(ids, data, ids)
  }
}

case class Scatter(ids: Seq[String], data: Seq[(Double, Double)], labels: Seq[String]) {
  require(ids.size == labels.size)
  require(data.size == labels.size)

  // this with only the given ids retained, and sorted in ascending id order
  def trimmed(keep: Set[String]) = {
    // horrible Zipped doesn't allow sortBy, so convert to Seq[(,,)]
    val zipped = (ids, data, labels).zipped.map { (a, b, c) => (a, b, c) }
    val (i, d, l) = zipped.filter(p => keep(p._1)).sortBy(_._1).unzip3
    Scatter(i, d, l)
  }

  // return the tuple of samples from this and that which are mutually compatible for comparisons
  // with the same ordering of ids.
  def compatible(that: Scatter) = {
    val common = ids.toSet.intersect(that.ids.toSet)
    (trimmed(common), that.trimmed(common))
  }
}


// samples are placed into bins
// weights count the number of samples in each bin
// labels are aggregated from samples in each bin
case class Histogram(range: NumericRange[Double], weights: Seq[Int], labels: Seq[String]) {
  require(weights.size == labels.size)
}

object Histogram {
  def apply(samples: Samples, xlims: (Double, Double), intervals: Int): Histogram = {
    val min = xlims._1
    val max = xlims._2
    val range = Range.Double(min, max, (max - min) / intervals)
    Histogram(samples, range)
  }

  def apply(samples: Samples, range: NumericRange[Double]): Histogram = {
    val (counts, labels) = bins(range, samples.data, samples.labels)
    Histogram(range, counts, labels)
  }

  private def bins(range: NumericRange[Double], data: Seq[Double], labels: Seq[String]) = {
    val zipped = data zip labels
    def inRange(interval: Double, p: Double) = p >= interval && p < interval + range.step
    range map { interval =>
      val count = data.count(inRange(interval, _))
      val labs = {
        zipped.collect {
          case (p, label) if inRange(interval, p) => label
          case _ => ""
        }
      }.distinct.filterNot(_.isEmpty)
      val label = if (labs.size > 10) (labs take 10) :+ "..." else labs
      (count, label.mkString(","))
    }
  }.unzip
}

// a normalised version of Histogram
case class Frequency(range: NumericRange[Double], weights: Seq[Double], labels: Seq[String]) {
  require(weights.sum == 0 || (weights.sum >= 99.9 && weights.sum <= 100.1), weights.sum)
}

object Frequency {
  def apply(hist: Histogram): Frequency = {
    val freqs = hist.weights.sum.toDouble match {
      case total if total <= 0 => hist.weights map { w => 0.0 }
      case total => hist.weights map { _ * 100 / total }
    }
    Frequency(hist.range, freqs, hist.labels)
  }
}

// https://developers.google.com/chart/interactive/docs/reference#DataTable
trait GoogleChartsSupport {
  protected def midpoint(range: NumericRange[Double], i: Int) = range(i) + range.step / 2

  def freqToDataTable(xlab: String, data: Map[String, Frequency], addLabels: Boolean = true) = {
    val (ylabs, freqs) = data.toSeq.unzip
    val ranges = freqs.map { _.range }.distinct
    require(ranges.size == 1, "all frequency tables must use the same range: " + ranges)
    val range = freqs.map { _.range }.head

    val headers = DataHeader(xlab) +: ylabs.flatMap { t =>
      DataHeader(t) :: {if (addLabels) RoleHeader("tooltip") :: Nil else Nil}
    }
    val rows = (0 until range.size) map { i =>
      Row(
        DataCell(midpoint(range, i)) +: freqs.flatMap { case Frequency(_, weights, labels) =>
          DataCell(weights(i)) :: {if (addLabels) LabelCell(labels(i)) :: Nil else Nil}
        }
      )
    }
    DataTable(headers, rows)
  }

  def scatterToDataTable(xlab: String, data: Map[String, Scatter]) = {
    val (ylabs, scatters) = data.toSeq.unzip

    val headers = DataHeader(xlab) +: ylabs.flatMap { t =>
      DataHeader(t) :: RoleHeader("tooltip") :: Nil
    }
    import LabelCell.NullCell
    val rows = (0 until data.size) flatMap { i =>
      scatters(i) match {
        case Scatter(_, weights, labels) =>
          (weights zip labels) map {
            case ((x, y), label) =>
              Row(
                DataCell(x) ::
                  List.fill(i * 2)(NullCell) :::
                  DataCell(y) :: LabelCell(label) ::
                  List.fill((data.size - i - 1) * 2)(NullCell)
              )
          }
      }
    }
    DataTable(headers, rows)
  }


  def samplesToDataTable(samples: Samples, title: String = "value") = {
    import samples.ids
    import samples.data
    import samples.labels
    val header = DataHeader("id", "string") :: DataHeader(title) ::
      RoleHeader("tooltip", label=Some("description")) :: Nil
    val body = (ids zip labels zip data).map { case ((id, label), sample) =>
      Row(LabelCell(id) :: DataCell(sample, Some("%.2f" format sample)) :: LabelCell(label) :: Nil)
    }
    DataTable(header, body)
  }

}
