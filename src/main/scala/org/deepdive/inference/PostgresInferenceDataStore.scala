package org.deepdive.inference

import anorm._
import au.com.bytecode.opencsv.CSVWriter
import java.io.{ByteArrayInputStream, File, FileOutputStream, FileWriter, StringWriter, Reader, FileReader, InputStream, InputStreamReader}
import org.deepdive.calibration._
import org.deepdive.datastore._
import org.deepdive.inference._
import org.deepdive.Logging
import org.deepdive.settings._
import org.deepdive.helpers.Helpers
import scala.collection.mutable.{ArrayBuffer, Set, SynchronizedBuffer}
import scala.io.Source
import java.io._

/* Stores the factor graph and inference results in a postges database. */
trait PostgresInferenceDataStoreComponent extends SQLInferenceDataStoreComponent {

  class PostgresInferenceDataStore(val dbSettings : DbSettings) extends SQLInferenceDataStore with Logging with PostgresDataStoreComponent {

    // Default batch size, if not overwritten by user
    val BatchSize = Some(250000)
      

    /**
     * weightsFile: location to the binary format. Assume "weightsFile.text" file exists.
     */
    def bulkCopyWeights(weightsFile: String, dbSettings: DbSettings) : Unit = {
    
     val cmdfile = File.createTempFile(s"copy", ".sh")
     val writer = new PrintWriter(cmdfile)
     val copyStr = List("psql ", Helpers.getOptionString(dbSettings), " -c ", "\"", 
       """\COPY """, s"${WeightResultTable}(id, weight) FROM \'${weightsFile}\' DELIMITER ' ';", "\"").mkString("")
     log.info(copyStr)
     writer.println(copyStr)
     writer.close()
     Helpers.executeCmd(cmdfile.getAbsolutePath())
    }
    
    def bulkCopyVariables(variablesFile: String, dbSettings: DbSettings) : Unit = {
     
     val cmdfile = File.createTempFile(s"copy", ".sh")
     val writer = new PrintWriter(cmdfile)
     val copyStr = List("psql ", Helpers.getOptionString(dbSettings), " -c ", "\"", 
       """\COPY """, s"${VariableResultTable}(id, category, expectation) FROM \'${variablesFile}\' DELIMITER ' ';", "\"").mkString("")
     log.info(copyStr)
     writer.println(copyStr)
     writer.close()
     Helpers.executeCmd(cmdfile.getAbsolutePath())
   }

    /**
    * This query optimizes slow joins on certain DBMS (MySQL) by creating indexes
    * on the join condition column.
    */
    def createIndexForJoinOptimization(relation: String, column: String) = {}

    /**
     * For postgres, do not create indexes for query table
     */
    override def createIndexesForQueryTable(queryTable: String, weightVariables: Seq[String]) = {
      // do nothing
    }

    /**
     * This query is datastore-specific since it creates a view whose 
     * SELECT contains a subquery in the FROM clause.
     * In Mysql the subqueries have to be created as views first.
     */
    def createCalibrationViewBooleanSQL(name: String, bucketedView: String, columnName: String) = s"""
        CREATE OR REPLACE VIEW ${name} AS
        SELECT b1.bucket, b1.num_variables, b2.num_correct, b3.num_incorrect FROM
        (SELECT bucket, COUNT(*) AS num_variables from ${bucketedView} GROUP BY bucket) b1
        LEFT JOIN (SELECT bucket, COUNT(*) AS num_correct from ${bucketedView} 
          WHERE ${columnName}=true GROUP BY bucket) b2 ON b1.bucket = b2.bucket
        LEFT JOIN (SELECT bucket, COUNT(*) AS num_incorrect from ${bucketedView} 
          WHERE ${columnName}=false GROUP BY bucket) b3 ON b1.bucket = b3.bucket 
        ORDER BY b1.bucket ASC;
        """

    /**
     * This query is datastore-specific since it creates a view whose 
     * SELECT contains a subquery in the FROM clause.
     */
    def createCalibrationViewMultinomialSQL(name: String, bucketedView: String, columnName: String) = s"""
        CREATE OR REPLACE VIEW ${name} AS
        SELECT b1.bucket, b1.num_variables, b2.num_correct, b3.num_incorrect FROM
        (SELECT bucket, COUNT(*) AS num_variables from ${bucketedView} GROUP BY bucket) b1
        LEFT JOIN (SELECT bucket, COUNT(*) AS num_correct from ${bucketedView} 
          WHERE ${columnName} = category GROUP BY bucket) b2 ON b1.bucket = b2.bucket
        LEFT JOIN (SELECT bucket, COUNT(*) AS num_incorrect from ${bucketedView} 
          WHERE ${columnName} != category GROUP BY bucket) b3 ON b1.bucket = b3.bucket 
        ORDER BY b1.bucket ASC;
        """
  }
}
