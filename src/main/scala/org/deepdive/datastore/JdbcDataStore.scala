package org.deepdive.datastore

import java.sql.Connection
import java.sql.{Connection, DriverManager, ResultSet};
import scalikejdbc._
import scalikejdbc.config._
import org.deepdive.Logging
import com.typesafe.config._

trait JdbcDataStore extends Logging {

  def DB = scalikejdbc.DB

  /* Borrows a connection from the connection pool. You should close the connection when done. */
  def borrowConnection() : Connection = ConnectionPool.borrow()

  /* Executes a block with a borrowed connection */
  def withConnection[A](block: Connection => A) = using(ConnectionPool.borrow())(block)

  /* Closes the connection pool and all of its connections */
  def close() = ConnectionPool.closeAll()

  def bulkInsert(outputRelation: String, data: Iterator[Map[String, Any]])(implicit session: DBSession) = {
    val columnNames = PostgresDataStore.DB.getColumnNames(outputRelation).sorted
    val columnValues = columnNames.map (x => "?")
    val copySQL = s"""INSERT INTO ${outputRelation}(${columnNames.mkString(", ")}) 
      VALUES (${columnValues.mkString(", ")})"""
    val tuples = data.map { tuple =>
      columnNames.map(c => tuple.get(c).orElse(tuple.get(c.toLowerCase)).getOrElse(null))
    }.toSeq
    log.debug(s"copying num_records=${tuples.size} into relation=${outputRelation}")
    SQL(copySQL).batch(tuples: _*).apply()
  }

}

object JdbcDataStore extends Logging {

  def executeCmd(cmd: String) {
    val conn = ConnectionPool.borrow()
    val stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_UPDATABLE)
    try {
      var prep = null
      """;\s+""".r.split(cmd.trim()).filterNot(_.isEmpty).foreach(q => conn.prepareStatement(q.trim()).executeUpdate)
    } catch {
      // SQL cmd exception
      case exception : Throwable =>
      log.error(exception.toString)
      throw exception
    } finally {
      conn.close()
    }
  }

  class JdbcDBsWithEnv(envValue: String, configObj: Config) extends DBsWithEnv(envValue) {
    override lazy val config = configObj
  }

  /* Initializes the data stores */
  def init(config: Config) : Unit = {
    val initializer = new JdbcDBsWithEnv("deepdive", config)
      log.info("Intializing all JDBC data stores")
      initializer.setupAll()
  }

  def init() : Unit = init(ConfigFactory.load)

  /* Closes the data store */
  def close() = {
    log.info("Closing all JDBC data stores")
    DBsWithEnv("deepdive").closeAll()
  }
  
}