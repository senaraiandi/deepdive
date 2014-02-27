package org.deepdive.extraction.datastore

import au.com.bytecode.opencsv.CSVWriter
import java.io.{File, StringWriter, FileWriter, PrintWriter, BufferedWriter, Writer}
import java.lang.RuntimeException
import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong
import org.deepdive.Context
import org.deepdive.datastore.{PostgresDataStore, DataStoreUtils}
import org.deepdive.Logging
import org.deepdive.settings._
import scala.collection.JavaConversions._
import play.api.libs.json._
import scala.util.{Try, Success, Failure}
import scalikejdbc._

trait PostgresExtractionDataStoreComponent extends ExtractionDataStoreComponent {
  val dataStore = new PostgresExtractionDataStore
}

class PostgresExtractionDataStore extends ExtractionDataStore[JsObject] with JdbcExtractionDataStore with Logging {

    /* Globally unique variable id for this data store */
    private val variableIdCounter = new AtomicLong(0)

    def init() = {
      variableIdCounter.set(0)
    }

    def ds = PostgresDataStore

    def addBatch(result: Iterator[JsObject], outputRelation: String) : Unit = {
      val file = File.createTempFile(s"deepdive_$outputRelation", ".csv")
      log.info(s"Writing data of to file=${file.getCanonicalPath}")
      val writer = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))
      // Write the dataset to the file for the relation
      writeCopyData(result, writer)
      writer.close()
      val columnNames = scalikejdbc.DB.getColumnNames(outputRelation).toSet
      val copySQL = buildCopySql(outputRelation, columnNames)
      log.info(s"Copying batch data to postgres. sql='${copySQL}'" +
        s"file='${file.getCanonicalPath}'")
      PostgresDataStore.withConnection { implicit connection =>
        Try(PostgresDataStore.copyBatchData(copySQL, file)) match {
          case Success(_) => 
            log.info("Successfully copied batch data to postgres.") 
            file.delete()
          case Failure(ex) => 
            log.error(s"Error during copy: ${ex}")
            log.error(s"Problematic CSV file can be found at file=${file.getCanonicalPath}")
            throw ex
        }
      } 
    }

    /* Builds a COPY statement for a given relation and column names */
    def buildCopySql(relationName: String, keys: Set[String]) = {
      val fields = keys.filterNot(_ == "id").toList.sorted
      s"""COPY ${relationName}(${fields.mkString(", ")}) FROM STDIN CSV"""
    }

    /* Builds a CSV dat astring for given JSON data and column names */
    def writeCopyData(data: Iterator[JsObject], fileWriter: Writer) : Unit = {
      val writer = new CSVWriter(fileWriter)
      for (obj <- data) { 
        val dataList = obj.value.filterKeys(_ != "id").toList.sortBy(_._1)
        val strList = dataList.map (x => jsValueToString(x._2))
        // We get a unique id for the record
        // val id = variableIdCounter.getAndIncrement()
        writer.writeNext(strList.toArray)
      }
    }

    /* Translates a JSON value to a String that can be insert using COPY statement */
    private def jsValueToString(x: JsValue) : String = x match {
      case JsString(x) => x.replace("\\", "\\\\")
      case JsNumber(x) => x.toString
      case JsNull => null
      case JsBoolean(x) => x.toString
      case JsArray(x) => 
        val innerData = x.map {
          case JsString(x) => 
            val convertedStr = jsValueToString(JsString(x))
            val escapedStr = convertedStr.replace("\"", "\\\"")
            s""" "${escapedStr}" """ 
          case x: JsValue => jsValueToString(x)
        }.mkString(",")
        val arrayStr = s"{${innerData}}"
        arrayStr
      case x : JsObject => Json.stringify(x)
      case _ =>
        log.warning(s"Could not convert JSON value ${x} to String")
        ""
    }

  }
