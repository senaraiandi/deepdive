package org.deepdive


import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config._
import org.deepdive.context.{ContextManager, FactorTaskOrdering}
import org.deepdive.datastore.{PostgresDataStore}
import org.deepdive.inference.{InferenceManager, FactorGraphBuilder}
import org.deepdive.context.{Context, Settings}
import org.deepdive.extraction.{ExtractionManager, ExtractionTask}
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.util.Sorting

object Pipeline {

  def run(config: Config) {

    val log = Logging.getLogger(Context.system, this)

    // Get the actor system
    val system = Context.system

    // Load Settings
    Settings.loadFromConfig(config)    

    // Initialize the data store
    PostgresDataStore.init(Settings.databaseUrl, Settings.get().connection.user, Settings.get().connection.password)

    // Start the Context manager
    val contextManager = system.actorOf(ContextManager.props, "ContextManager")

    // Start the Inference Manager
    val inferenceManager = system.actorOf(InferenceManager.props(contextManager, Settings.databaseUrl), "InferenceManager")

    // TODO: ETL Tasks: We ignore this for now

    // TODO: Have an extraction manager that manages and parallelizes the extractions
    // Start the ExtractorExecutor for each defined Extractor
    val extractionManager = system.actorOf(ExtractionManager.props(Settings.databaseUrl), "ExtractionManager")
    // val extractorExecutor = system.actorOf(ExtractorExecutor.props(Settings.databaseUrl), "ExtractorExecutor")

    implicit val timeout = Timeout(30 minutes)
    implicit val ec = system.dispatcher
    
    // Run extractions
    log.debug("Running extractors")
    val extractionResults = for {
      extractor <- Settings.get().extractors
      relation <- Settings.getRelation(extractor.outputRelation)
      task <- Some(ExtractionTask(extractor.name, extractor.outputRelation, extractor.inputQuery, extractor.udf))
      extractionResult <- Some(ask(extractionManager, ExtractionManager.AddTask(task)))
    } yield extractionResult

    Await.result(Future.sequence(extractionResults), 30 minutes)

    // Build the factor graph
    log.debug("Building factor graph")
    for {
      relation <- Settings.get().relations.sorted(FactorTaskOrdering)
      factor <- Option(Settings.extractorForRelation(relation.name).map(_.factor))
      graphResult <- ask(inferenceManager, 
        FactorGraphBuilder.AddFactorsForRelation(relation.name, relation, factor))
    }

    system.shutdown()
    system.awaitTermination()

  }

}