package org.deepdive.extraction

import anorm._
import spray.json._
import akka.actor.{Actor, ActorRef, Props, ActorLogging}
import org.deepdive.context.{Relation, Settings}
import org.deepdive.datastore.{PostgresDataStore => DB}

case class ExtractionTask(name: String, outputRelation: String, inputQuery: String, udf: String)

object ExtractorExecutor {
  def props(databaseUrl: String): Props = Props(classOf[ExtractorExecutor], databaseUrl)
  case class Execute(task: ExtractionTask)

  def buildInsert(relation: Relation) = {
    val relation_fields =  "(" + relation.schema.keys.filterNot(_ == "id").mkString(", ") + ")"
    val relationPlaceholders =  "(" + relation.schema.keys.filterNot(_ == "id").map { field =>
      "{" + field + "}"
    }.mkString(", ") + ")"
    s"INSERT INTO ${relation.name} ${relation_fields} VALUES ${relationPlaceholders};"
  }

  def buildTupleMap(relation: Relation, result: List[JsArray]) : 
    Seq[Seq[(String, ParameterValue[_])]] = {
    val keys = relation.schema.keys.filterNot(_ == "id")
    val schemaDomain = relation.schema.filterKeys(_ != "id").values.toList
    val values = result.map { tuple =>
      tuple.elements.zipWithIndex.map { case (x,i) =>
        schemaDomain(i) match {
          case "String" => toParameterValue(x.compactPrint)
          case "Integer" => toParameterValue(x.compactPrint.toInt)
        }
      }
    }
    values.map { tuple => keys.zip(tuple).toSeq }.toSeq
  }

}

class ExtractorExecutor(databaseUrl: String) extends Actor with ActorLogging {

  val WINDOW_SIZE = 1000

  override def preStart() {
    log.debug("Starting")
  }
  
  def receive = {
    case ExtractorExecutor.Execute(task) => 
      val manager = sender
      doExecute(task)
      log.debug(s"Finished executing task ${task.name}")
      manager ! ExtractionManager.TaskCompleted(task.name)
    case _ =>
      log.warning("Huh?")
  }

  private def doExecute(task: ExtractionTask) {
    log.debug(s"Executing $task")
    val executor = new ScriptTaskExecutor(task)
    val result = executor.run()
    writeResult(result, task.outputRelation)
  }

  private def writeResult(result: List[JsArray], outputRelation: String) {
    val relation = Settings.getRelation(outputRelation).orNull
    val insertStatement = result match {
      case Nil => ""
      case _ => ExtractorExecutor.buildInsert(relation)
    }
    log.debug(s"Writing extraction result back to the database, length=${result.length}")
    DB.withConnection { implicit conn =>
      val sqlStatement = SQL(insertStatement)
      result.grouped(WINDOW_SIZE).zipWithIndex.foreach { case(window, i) =>
        log.debug(s"${WINDOW_SIZE * i}/${result.size}")
        val tuples = ExtractorExecutor.buildTupleMap(relation, window)
        val batchInsert = new BatchSql(sqlStatement, tuples)
        batchInsert.execute()
      }
    }
    log.debug(s"Wrote num=${result.length} records.")
  }

}