package org.deepdive.inference

import anorm._
import java.sql.Connection
import akka.event.Logging
import scala.collection.mutable.{Map, ArrayBuffer, Set}
import org.slf4j.LoggerFactory
import org.deepdive.context.Context
import org.deepdive.datastore.Utils.AnormSeq

class PostgresFactorStore(implicit val connection: Connection) {

  val BATCH_SIZE = 5000

  val log = Logging.getLogger(Context.system, this)

  val factorFunctions = Map[String, FactorFunction]()
  val variables = Map[(String, Long), Variable]()
  val factors = ArrayBuffer[Factor]()
  val weights = Map[String, Weight]()

  // Prepares the data store to store the factor graph
  def init() {
    // weights(id, value, is_fixed)
    SQL("""drop table if exists weights; create table weights(id bigint primary key, 
      value double precision, is_fixed boolean);""").execute()
    // factor_functions(id, description)
    SQL("""drop table if exists factor_functions; 
      create table factor_functions(id bigint primary key, description text);""").execute()
    // variables(id, variable_type, lower_bound, upper_bound, initial_value)
    SQL("""drop table if exists variables; 
      create table variables(id bigint primary key, variable_type varchar(4), 
      lower_bound double precision, upper_bound double precision, 
      initial_value double precision);""").execute()
    // factors(id, weight_id, factor_function_id)
    SQL("""drop table if exists factors; create table 
      factors(id bigint primary key, weight_id bigint, factor_function_id bigint);""").execute()
    // factor_variables(factor_id, variable_id, position, is_positive)
    SQL("""drop table if exists factor_variables; 
      create table factor_variables(factor_id bigint, variable_id bigint, 
      position int, is_positive boolean);""").execute()
    
  }

  def addFactorFunction(factorName: String, factorFunction: FactorFunction) = {
    factorFunctions += Tuple2(factorName, factorFunction)
  }

  def getFactorFunction(factorName: String) : Option[FactorFunction] = factorFunctions.get(factorName)

  // Add a new Factor to the datastore
  def addFactor(factor: Factor) {
    factors += factor
  }

  // Add a new Variable to the datastore
  def addVariable(relationName: String, localId: Long, variable: Variable) {
    variables += Tuple2((relationName, localId), variable)
  }

  // Get a specific variable from the data store
  def getVariable(relationName: String, localId: Long) : Option[Variable] = {
    variables.get((relationName, localId))
  }

  // Get a specific weight form the data store
  def getWeight(identifier: String) = weights.get(identifier)

  // Add a weight to the data store
  def addWeight(identifier: String, weight: Weight) = { weights += Tuple2(identifier, weight) }

  // Flush out
  def flush(relationName: String) {
    
    // Insert Weights
    log.debug(s"Storing num=${weights.size} relation=weights")
    writeWeights(weights.values)
    weights.clear()

    // Insert Factor Functions
    log.debug(s"Storing num=${factorFunctions.size} relation=factor_functions")
    writeFactorFunctions(factorFunctions.values)
    factorFunctions.clear()

    // Insert Variables
    val relationVariables = variables.filterKeys(_._1 == relationName).values
    val numEvidenceVariables = relationVariables.count(_.variableType == VariableType.CES)
    val numQueryVariables = relationVariables.count(_.variableType == VariableType.CQS)
    log.debug(s"Storing num=${relationVariables.size} num_evidence=${numEvidenceVariables} " +
      s"num_query=${numQueryVariables} relation=variables")
    writeVariables(relationVariables)

    // Insert Factors 
    log.debug(s"Storing num=${factors.size} relation=factors")
    writeFactors(factors)

    // Insert Factor Variables
    val factorVariables = factors.flatMap(_.variables)
    log.debug(s"Storing num=${factorVariables.size} relation=factor_variables")
    writeFactorVariables(factorVariables)
    factors.clear()
    
  }

  private def writeWeights(values: Iterable[Weight]) {
    val sqlStatement = SQL("insert into weights(id, value, is_fixed) values ({id}, {value}, {is_fixed})")
    writeBatch(sqlStatement, values.iterator.map(Weight.toAnormSeq(_)))
  }

  private def writeFactorFunctions(values: Iterable[FactorFunction]) {
    val sqlStatement = SQL("insert into factor_functions(id, description) values ({id}, {description})")
    writeBatch(sqlStatement, values.iterator.map(FactorFunction.toAnormSeq))
  }

  private def writeVariables(values: Iterable[Variable]) {
    val sqlStatement = SQL("""insert into variables(id, variable_type, lower_bound, upper_bound, initial_value) 
      values ({id}, {variable_type}, {lower_bound}, {upper_bound}, {initial_value})""")
    writeBatch(sqlStatement, values.iterator.map(Variable.toAnormSeq))
  }

  private def writeFactors(values: Iterable[Factor]) {
    val sqlStatement = SQL("""insert into factors (id, weight_id, factor_function_id)
      values ({id}, {weight_id}, {factor_function_id})""")
    writeBatch(sqlStatement, values.iterator.map(Factor.toAnormSeq))
  }

  private def writeFactorVariables(values: Iterable[FactorVariable]) {
    val sqlStatement = SQL("""insert into factor_variables(factor_id, variable_id, position, is_positive)
      values ({factor_id}, {variable_id}, {position}, {is_positive})""");
    writeBatch(sqlStatement, values.iterator.map(FactorVariable.toAnormSeq))
  }


  private def writeBatch(sqlStatement: SqlQuery, values: Iterator[AnormSeq]) {
    values.grouped(BATCH_SIZE).zipWithIndex.foreach { case(batch, i) =>
      // log.debug(s"${BATCH_SIZE * i}")
      val batchInsert = new BatchSql(sqlStatement, batch.toSeq)
      batchInsert.execute()
    }
  }



}