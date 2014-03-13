package org.deepdive.inference

import org.deepdive.Logging
import org.deepdive.settings.VariableDataType

// // Variable Type: CQS (Discrete, Query, Gibbs Sampling) or CES (Discrete, Evidence, Gibbs Sampling)
// object VariableDataType extends Enumeration with Logging {
//   type VariableDataType = Value
//   val `Boolean`, Discrete, Continuous = Value
// }
// import VariableDataType._

case class Variable(id: Long, dataType: VariableDataType, initialValue: Option[Double], 
  isEvidence: Boolean, isQuery: Boolean, mappingRelation: String, 
  mappingColumn: String, mappingId: Long) extends CSVFormattable {
  
  def toCSVRow = Array(id.toString, dataType.toString, initialValue.map(_.toString).getOrElse(null), 
    isEvidence.toString, isQuery.toString, mappingRelation, mappingColumn, mappingId.toString)
}