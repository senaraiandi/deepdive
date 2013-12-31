package org.deepdive.extraction

import anorm._
import akka.actor.{Actor, ActorRef, Props, ActorLogging}
import org.deepdive.settings._
import org.deepdive.Context
import org.deepdive.datastore.{PostgresDataStore => DB}
import org.deepdive.extraction._
import org.deepdive.extraction.datastore._
import org.deepdive.Logging
import scala.util.{Try, Success, Failure}
import scala.concurrent._
import scala.concurrent.duration._
import rx.lang.scala.subjects._


object ExtractorExecutor {
  
  def props(dataStore: ExtractionDataStoreComponent#ExtractionDataStore): Props = 
    Props(classOf[ExtractorExecutor], dataStore)

  // Messages we can receive
  sealed trait Message
  case class ExecuteTask(task: ExtractionTask)
}

/* Executes a single extraction task, shuts down when done. */
class ExtractorExecutor(dataStore: ExtractionDataStoreComponent#ExtractionDataStore) extends Actor with ActorLogging  { 

  import ExtractorExecutor._

  override def preStart() {
    log.info("Starting")
  }

  def receive = {
    case ExecuteTask(task) => 
      log.info(s"Executing $task")
      val taskResult = doExecute(task)
      log.info(s"Finished executing task_name=${task.extractor.name}")
      context.parent ! taskResult
      context.stop(self)
    case _ =>
      log.warning("Huh?")
  }

  def doExecute(task: ExtractionTask) : ExtractionTaskResult = {

    val executor = new ScriptTaskExecutor(task, dataStore.queryAsJson(task.extractor.inputQuery))
    val result = executor.run()

    // We execute writing to the database asynchronously because it may be a long operation
    // TODO: We are using Akka's default dispatcher here, maybe we should define our own.
    val writtenResults = result.rows.flatMap { rowBatch =>
      import context.dispatcher
      val writeFuture = Future { dataStore.write(rowBatch, task.extractor.outputRelation) }
      val subject = AsyncSubject[Unit]()
      writeFuture onComplete {
        case Failure(x) => { subject.onError(x) }
        case Success(x) => { subject.onNext(x); subject.onCompleted() }
      }
      subject
    }

    // Handle the results of writing back to the database.
    // Block until all results are written
    val isDone = Promise[ExtractionTaskResult]()
    writtenResults.subscribe(
      rowBatch => {},
      exception => log.error(exception.toString),
      () => isDone.success(ExtractionTaskResult(task, Success[Unit]()))
    )
    Await.result(isDone.future, Duration.Inf)
  }

}


