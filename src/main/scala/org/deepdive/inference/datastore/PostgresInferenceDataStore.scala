package org.deepdive.inference

import anorm._
import au.com.bytecode.opencsv.CSVWriter
import java.io.{ByteArrayInputStream, File, FileOutputStream, StringWriter}
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.deepdive.settings.FactorFunctionVariable
import org.deepdive.datastore.PostgresDataStore
import org.deepdive.Logging
import scala.collection.mutable.{ArrayBuffer, Set}
import scala.io.Source

/* Stores the factor graph and inference results in a postges database. */
trait PostgresInferenceDataStoreComponent extends InferenceDataStoreComponent {

  lazy val inferenceDataStore = new PostgresInferenceDataStore

  class PostgresInferenceDataStore extends InferenceDataStore with Logging {

    implicit lazy val connection = PostgresDataStore.borrowConnection()

    // TODO: We should tune this based on experiments.
    val BatchSize = Some(50000)

    // We keep track of the variables, weights and factors already added
    // These will be kept in memory at all times.
    // TODO: Ideally, we don't keep anything in memory and resolve conflicts in the database.
    val variableIdSet = Collections.newSetFromMap[Long](
      new ConcurrentHashMap[Long, java.lang.Boolean]())
    val weightIdSet = Collections.newSetFromMap[Long](
      new ConcurrentHashMap[Long, java.lang.Boolean]())
    val factorIdSet = Collections.newSetFromMap[Long](
      new ConcurrentHashMap[Long, java.lang.Boolean]())
    
    // Temorary buffer for the next batch.
    // These collections will the cleared when we write the next batch to postgres
    val variables = ArrayBuffer[Variable]()
    val factors = ArrayBuffer[Factor]()
    val weights = ArrayBuffer[Weight]()
    
    def init() : Unit = {
      
      variableIdSet.clear()
      weightIdSet.clear()
      factorIdSet.clear()
      variables.clear()
      factors.clear()
      weights.clear()

      // weights(id, initial_value, is_fixed, description)
      SQL("""drop table if exists weights; 
        create table weights(id bigint primary key, 
        initial_value double precision, is_fixed boolean, description text);""").execute()
      
      // factors(id, weight_id, factor_function)
      SQL("""drop table if exists factors CASCADE; 
        create table factors(id bigint primary key, 
        weight_id bigint, factor_function text);""").execute()

      // variables(id, data_type, initial_value, is_evidence, is_query, mapping_relation, mapping_column)
      SQL("""drop table if exists variables CASCADE; 
        create table variables(id bigint primary key, data_type text,
        initial_value double precision, is_evidence boolean, is_query boolean,
        mapping_relation text, mapping_column text, mapping_id bigint);""").execute()
      
      // factor_variables(factor_id, variable_id, position, is_positive)
      SQL("""drop table if exists factor_variables; 
        create table factor_variables(factor_id bigint, variable_id bigint, 
        position int, is_positive boolean);""").execute()
      SQL("CREATE INDEX ON factor_variables using hash (factor_id);").execute()
      SQL("CREATE INDEX ON factor_variables using hash (variable_id);").execute()

      // inference_result(id, last_sample, probability)
      SQL("""drop table if exists inference_result CASCADE; 
        create table inference_result(id bigint primary key, last_sample boolean, 
        probability double precision);""").execute()
      SQL("CREATE INDEX ON inference_result using btree (probability);").execute()

      // A view for the mapped inference result.
      // The view is a join of the variables and inference result tables.
      SQL("""drop view if exists mapped_inference_result; 
      CREATE VIEW mapped_inference_result AS SELECT variables.*, inference_result.last_sample, inference_result.probability 
      FROM variables INNER JOIN inference_result ON variables.id = inference_result.id;
      """).execute()
    }

    def getLocalVariableIds(rowMap: Map[String, Any], factorVar: FactorFunctionVariable) : Array[Long] = {
      if (factorVar.isArray)
        // Postgres prefixes aggregated colimns with a dot
        rowMap(s".${factorVar.relation}.id").asInstanceOf[Array[Long]]
      else
        Array(rowMap(s"${factorVar.relation}.id").asInstanceOf[Long])
    }

    def addFactor(factor: Factor) = { 
      if (!factorIdSet.contains(factor.id)) {
        factors += factor
        factorIdSet.add(factor.id)
      }
    }

    def addVariable(variable: Variable) = {
      if (!variableIdSet.contains(variable.id)) {
        variables += variable
        variableIdSet.add(variable.id)
      }
    }
    
    def addWeight(weight: Weight) = { 
      if (!weightIdSet.contains(weight.id)) {
        weights += weight
        weightIdSet.add(weight.id)
      }
    }

    def dumpFactorGraph(variablesFile: File, factorsFile: File, weightsFile: File) : Unit = {
      // Write the weights file
      log.info(s"Writing weights to file=${weightsFile.getAbsolutePath}")
      copySQLToFile("""SELECT id, initial_value, 
        case when is_fixed then 'true' else 'false' end,
        description
        FROM weights""", weightsFile)

      // Write factors file
      log.info(s"Writing factors to file=${factorsFile.getAbsolutePath}")
      copySQLToFile("SELECT id, weight_id, factor_function FROM factors", factorsFile)

      // Write variables file
      log.info(s"Writing factor_map to file=${variablesFile.getAbsolutePath}")
      copySQLToFile("""SELECT variables.id, factor_variables.factor_id, factor_variables.position,
        case when factor_variables.is_positive then 'true' else 'false' end, 
        variables.data_type, variables.initial_value, 
        case when variables.is_evidence then 'true' else 'false' end,
        case when variables.is_query then 'true' else 'false' end
        FROM variables LEFT JOIN factor_variables ON factor_variables.variable_id = variables.id""", 
      variablesFile)

    }

    def writebackInferenceResult(variableOutputFile: String) : Unit = {
      // Copy the inference result back to the database
      copyBatchData("COPY inference_result(id, last_sample, probability) FROM STDIN",
        Source.fromFile(variableOutputFile).getLines.mkString("\n"))
      
      // Each (relation, column) tuple is a variable in the plate model.
      // Find all (relation, column) combinations
      val relationsColumns = 
        SQL("SELECT DISTINCT mapping_relation, mapping_column from variables;")().map { row =>
        Tuple2(row[String]("mapping_relation"), row[String]("mapping_column"))
      }.toSet

      // Generate a view for each (relation, column) combination.
      relationsColumns.foreach { case(relationName, columnName) => 
        val view_name = s"${relationName}_${columnName}_inference"
        log.info(s"creating view=${view_name}")
        SQL(s"""DROP VIEW IF EXISTS ${view_name}; CREATE VIEW ${view_name} AS
          SELECT ${relationName}.*, mir.last_sample, mir.probability FROM
          ${relationName} JOIN
            (SELECT mir.last_sample, mir.probability, mir.id 
            FROM mapped_inference_result mir 
            WHERE mapping_relation = '${relationName}' AND mapping_column = '${columnName}') 
          mir ON ${relationName}.id = mir.mapping_id""").execute()
      }
    }

    def flush() : Unit = {
      // Insert weight
      log.debug(s"Storing num_weights=${weights.size}")
      copyBatchData("""COPY weights(id, initial_value, is_fixed, description) FROM STDIN CSV""", 
        toCSVData(weights.iterator))
      
      // Insert variables
      val numEvidence = variables.count(_.isEvidence)
      val numQuery = variables.count(_.isQuery)
      log.debug(s"Storing num_variables=${variables.size} num_evidence=${numEvidence} " +
        s"num_query=${numQuery}")
      copyBatchData("""COPY variables( id, data_type, initial_value, is_evidence, is_query,
        mapping_relation, mapping_column, mapping_id) FROM STDIN CSV""", 
        toCSVData(variables.iterator))
      
      // Insert factors 
      log.debug(s"Storing num_factors=${factors.size}")
      copyBatchData( """COPY factors(id, weight_id, factor_function) FROM STDIN CSV""", 
        toCSVData(factors.iterator))
      
      // Insert Factor Variables
      copyBatchData("""COPY factor_variables(factor_id, variable_id, position, is_positive) 
        FROM STDIN CSV""", toCSVData(factors.iterator.flatMap(_.variables)))
      
      // Clear the temporary buffer
      weights.clear()
      factors.clear()
      variables.clear()
    }

    // Converts CSV-formattable data to a CSV string
    private def toCSVData[T <: CSVFormattable](data: Iterator[T]) = {
      val strWriter = new StringWriter
      val writer = new CSVWriter(strWriter)
      data.foreach (obj => writer.writeNext(obj.toCSVRow))
      writer.close()
      strWriter.toString
    }

    // Executes a "COPY FROM STDIN" statement using a string of raw data */
    private def copyBatchData(sqlStatement: String, rawData: String) = {
      val del = new org.apache.commons.dbcp.DelegatingConnection(connection)
      val pg_conn = del.getInnermostDelegate().asInstanceOf[org.postgresql.core.BaseConnection]
      val cm = new org.postgresql.copy.CopyManager(pg_conn)
      val is = new ByteArrayInputStream(rawData.getBytes("UTF-8"))
      cm.copyIn(sqlStatement, is)
      is.close()
    }

    // Executes a SELECT statement and saves the result in a postgres text format file
    // (http://www.postgresql.org/docs/9.1/static/sql-copy.html#AEN64107)
    private def copySQLToFile(sqlSelect: String, f: File) = {
      val del = new org.apache.commons.dbcp.DelegatingConnection(connection)
      val pg_conn = del.getInnermostDelegate().asInstanceOf[org.postgresql.core.BaseConnection]
      val cm = new org.postgresql.copy.CopyManager(pg_conn)
      val os = new FileOutputStream(f)
      val copySql = s"COPY ($sqlSelect) TO STDOUT"
      cm.copyOut(copySql, os)
      os.close()
    }

  }
}