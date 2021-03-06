package org.broadinstitute.hail.driver

import org.apache.spark.sql.Row
import org.broadinstitute.hail.utils._
import org.broadinstitute.hail.annotations._
import org.broadinstitute.hail.expr._
import org.broadinstitute.hail.utils._
import org.kohsuke.args4j.{Option => Args4jOption}

object AnnotateSamplesTable extends Command with JoinAnnotator {

  class Options extends BaseOptions with TextTableOptions {
    @Args4jOption(required = true, name = "-i", aliases = Array("--input"),
      usage = "TSV file path")
    var input: String = _

    @Args4jOption(required = true, name = "-e", aliases = Array("--sample-expr"),
      usage = "Expression of columns to form sample ID")
    var sampleExpr: String = _

    @Args4jOption(required = false, name = "-r", aliases = Array("--root"),
      usage = "Period-delimited path starting with `sa' (this argument or --code required)")
    var root: String = _

    @Args4jOption(required = false, name = "-c", aliases = Array("--code"),
      usage = "Use annotation expressions to join with the table (this argument or --root required)")
    var code: String = _
  }

  def newOptions = new Options

  def name = "annotatesamples table"

  def description = "Annotate samples with a delimited text file"

  def supportsMultiallelic = true

  def requiresVDS = true

  def run(state: State, options: Options): State = {
    val vds = state.vds

    val (expr, code) = (Option(options.code), Option(options.root)) match {
      case (Some(c), None) => (true, c)
      case (None, Some(r)) => (false, r)
      case _ => fatal("this module requires one of `--root' or `--code', but not both")
    }

    val (struct, rdd) = TextTableReader.read(state.sc)(Array(options.input), options.config)

    val (finalType, inserter): (Type, (Annotation, Option[Annotation]) => Annotation) =
      if (expr) {
        val ec = EvalContext(Map(
          "sa" -> (0, vds.saSignature),
          "table" -> (1, struct)))
        buildInserter(code, vds.saSignature, ec, Annotation.SAMPLE_HEAD)
      } else
        vds.insertSA(struct, Parser.parseAnnotationRoot(code, Annotation.SAMPLE_HEAD))
    
    val sampleQuery = struct.parseInStructScope[String](options.sampleExpr, TString)

    val map = rdd
      .flatMap {
        _.map { a =>
          sampleQuery(a).map(s => (s, a))
        }.value
      }
      .collect()
      .toMap

    state.copy(vds = vds.annotateSamples(map.get _, finalType, inserter))
  }
}
