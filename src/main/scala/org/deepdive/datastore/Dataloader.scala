package org.deepdive.datastore

import org.deepdive.settings._
// import org.deepdive.datastore.JdbcDataStore
import org.deepdive.Logging
import org.deepdive.helpers.Helpers
import org.deepdive.helpers.Helpers.{Psql, Mysql}
import scala.sys.process._
import scala.util.{Try, Success, Failure}
import java.io._

class DataLoader extends JdbcDataStore with Logging {

  // def ds : JdbcDataStore

  /** Unload data of a SQL query from database to a TSV file 
   * 
   * For Greenplum, use gpfdist. Must specify gpport, gppath, gphost in dbSettings. No need for filepath
   * For Postgresql, filepath is an abosulute path. No need for dbSettings or filename.
   * For greenplum, use gpload; for postgres, use \copy
   * 
   * @param filename: the name of the output file
   * @param filepath: the absolute path of the output file
   * @param dbSettings: database settings (DD's class)
   * @param usingGreenplum: whether to use greenplum's gpunload
   * @param query: the query to be dumped
   */
  def unload(filename: String, filepath: String, dbSettings: DbSettings, usingGreenplum: Boolean, query: String) : Unit = {
    
    if (usingGreenplum) {
      val hostname = dbSettings.gphost
      val port = dbSettings.gpport
      val path = dbSettings.gppath

      if (path != "" && filename != "" && hostname != "") {
        new File(s"${path}/${filename}").delete()
      } else {
        throw new RuntimeException("greenplum parameters gphost, gpport, gppath are not set!")
      }

      // hacky way to get schema from a query...
      executeSqlQueries(s"""
        DROP VIEW IF EXISTS _${filename}_view CASCADE;
        DROP TABLE IF EXISTS _${filename}_tmp CASCADE;
        CREATE VIEW _${filename}_view AS ${query};
        CREATE TABLE _${filename}_tmp AS SELECT * FROM _${filename}_view LIMIT 0;
        """)

      executeSqlQueries(s"""
        DROP EXTERNAL TABLE IF EXISTS _${filename} CASCADE;
        CREATE WRITABLE EXTERNAL TABLE _${filename} (LIKE _${filename}_tmp)
        LOCATION ('gpfdist://${hostname}:${port}/${filename}')
        FORMAT 'TEXT';
        """)

      executeSqlQueries(s"""
        DROP VIEW _${filename}_view CASCADE;
        DROP TABLE _${filename}_tmp CASCADE;""")

      executeSqlQueries(s"""
        INSERT INTO _${filename} ${query};
        """)
    } else { // psql / mysql
  
      // Branch by database driver type (temporary solution)
      val dbtype = Helpers.getDbType(dbSettings)

      val sqlQueryPrefixRun = dbtype match {
        case Psql => "psql " + Helpers.getOptionString(dbSettings) + " -c "
        // -N: skip column names
        case Mysql => "mysql " + Helpers.getOptionString(dbSettings) + " --silent -N -e "
      }
  
      executeSqlQueries(s"""
        DROP VIEW IF EXISTS _${filename}_view CASCADE;
        CREATE VIEW _${filename}_view AS ${query};
        """)

      val outfile = new File(filepath)
      outfile.getParentFile().mkdirs()

      val cmdfile = File.createTempFile(s"unload", ".sh")
      val writer = new PrintWriter(cmdfile)
      
      // This query CANNOT contain double-quotes (").
      val copyStr = dbtype match {
        case Psql => List(sqlQueryPrefixRun + "\"", 
            """\COPY """, s"(SELECT * FROM _${filename}_view) TO '${filepath}'", "\"").mkString("")
            
        case Mysql => List(sqlQueryPrefixRun + "\"", 
            s"SELECT * FROM _${filename}_view", "\"", s" > ${filepath}").mkString("")
      }
        
      log.info(copyStr)
      writer.println(copyStr)
      writer.close()
      Helpers.executeCmd(cmdfile.getAbsolutePath())
      // executeSqlQuery(s"""COPY (SELECT * FROM _${filename}_view) TO '${filepath}';""")
      executeSqlQueries(s"DROP VIEW _${filename}_view;")
      s"rm ${cmdfile.getAbsolutePath()}".!
    }
  }
  
  /** Load data from a TSV file to database
   *
   * For greenplum, use gpload; for postgres, use \copy
   *
   * @param filepath: the absolute path of the input file, it can contain wildchar characters
   * @param tablename: the table to be copied to
   * @param dbSettings: database settings (DD's class)
   * @param usingGreenplum: whether to use greenplum's gpload
   */ 
  def load(filepath: String, tablename: String, dbSettings: DbSettings, usingGreenplum: Boolean) : Unit = {
    
    if (usingGreenplum) {
      val loadyaml = File.createTempFile(s"gpload", ".yml")
      val dbname = dbSettings.dbname
      val pguser = dbSettings.user
      val pgport = dbSettings.port
      val pghost = dbSettings.host
      if (dbname == null || pguser == null || pgport == null || pghost == null) {
        throw new RuntimeException("database settings (user, port, host, dbname) missing!")
      }
      val gpload_setting = s"""
        |VERSION: 1.0.0.1
        |DATABASE: ${dbname}
        |USER: ${pguser}
        |HOST: ${pghost}
        |PORT: ${pgport}
        |GPLOAD:
        |   INPUT:
        |      - MAX_LINE_LENGTH: 3276800
        |      - SOURCE:
        |         FILE:
        |            - ${filepath}
        |      - FORMAT: text
        |      - DELIMITER: E'\\t'
        |   OUTPUT:
        |      - TABLE: ${tablename}
      """.stripMargin

      val gploadwriter = new PrintWriter(loadyaml)
      gploadwriter.println(gpload_setting)
      gploadwriter.close()

      val cmdfile = File.createTempFile(s"gpload", ".sh")
      val cmdwriter = new PrintWriter(cmdfile)
      val cmd = s"gpload -f ${loadyaml.getAbsolutePath()}"
      cmdwriter.println(cmd)
      cmdwriter.close()

      log.info(cmd)
      Helpers.executeCmd(cmdfile.getAbsolutePath())
      cmdfile.delete()
      loadyaml.delete()
    } else {
      // Generate SQL query prefixes
      val dbtype = Helpers.getDbType(dbSettings)
  
      // NOTE: mysqlimport requires input file to have basename that is same as 
      // tablename!
      val cmdfile = File.createTempFile(s"${tablename}.copy", ".sh")
      val writer = new PrintWriter(cmdfile)
      val writebackPrefix = s"find ${filepath} -print0 | xargs -0 -P 1 -L 1 bash -c ";
      val writebackCmd = dbtype match {
        case Psql => writebackPrefix + s"'psql " + Helpers.getOptionString(dbSettings) + 
          "-c \"COPY " + s"${tablename} FROM STDIN;" + 
          " \" < $0'"
        case Mysql => writebackPrefix +
          s"'mysqlimport " + Helpers.getOptionString(dbSettings) + " $0'"
      }
      
      log.info(writebackCmd)
      writer.println(writebackCmd)
      writer.close()
      Helpers.executeCmd(cmdfile.getAbsolutePath())
      cmdfile.delete()
    }
  }

}