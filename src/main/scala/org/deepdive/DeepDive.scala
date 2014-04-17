package org.deepdive

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config._
import java.io.File
import org.deepdive.settings._
import org.deepdive.datastore.{JdbcDataStore}
import org.deepdive.extraction.{ExtractionManager, ExtractionTask, ExtractionTaskResult}
import org.deepdive.extraction.datastore._
import org.deepdive.inference.{InferenceManager, FactorGraphBuilder}
import org.deepdive.profiling._
import org.deepdive.calibration._
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.io.Source
import scala.util.{Try, Success, Failure}

object DeepDive extends Logging {

  def run(config: Config, outputDir: String) {

    // Get the actor system
    val system = Context.system
    Context.outputDir = outputDir

    // Create the output directory
    val outputDirFile = new File(outputDir)
    outputDirFile.mkdirs()
    log.info(s"outputDir=${outputDir}")

    // Load Settings
    val settings = Settings.loadFromConfig(config)

    val dbDriver = config.getString("deepdive.db.default.driver")
    
    // Setup the data store
    JdbcDataStore.init(config)
    settings.schemaSettings.setupFile.foreach { file =>
      log.info(s"Setting up the schema using ${file}")
      val cmd = Source.fromFile(file).getLines.mkString("\n")
      JdbcDataStore.executeCmd(cmd)
    }
    
    implicit val timeout = Timeout(1337 hours)
    implicit val ec = system.dispatcher

    // Start actors
    val profiler = system.actorOf(Profiler.props, "profiler")
    val taskManager = system.actorOf(TaskManager.props, "taskManager")
    val inferenceManager = system.actorOf(InferenceManager.props(
      taskManager, settings.schemaSettings.variables, dbDriver), "inferenceManager")
    val extractionManager = system.actorOf(
      ExtractionManager.props(settings.extractionSettings.parallelism, dbDriver), 
      "extractionManager")
    
    // Build tasks for extractors
    val extractionTasks = for {
      extractor <- settings.extractionSettings.extractors
      extractionTask = ExtractionTask(extractor)
    } yield Task(s"${extractor.name}", extractor.dependencies.toList, 
      extractionTask, extractionManager)

    // Build task to construct the factor graph
    val activeFactors = settings.pipelineSettings.activePipeline match { 
      case Some(pipeline) => 
        settings.inferenceSettings.factors.filter(f => pipeline.tasks.contains(f.name))
      case None => settings.inferenceSettings.factors
    }
    val groundFactorGraphMsg = InferenceManager.GroundFactorGraph(
      activeFactors, settings.calibrationSettings.holdoutFraction, settings.calibrationSettings.holdoutQuery,
        settings.inferenceSettings.skipLearning
    )
    val groundFactorGraphTask = Task("inference_grounding", extractionTasks.map(_.id), 
      groundFactorGraphMsg, inferenceManager)

    val inferenceTask = Task("inference", extractionTasks.map(_.id) ++ Seq("inference_grounding"),
      InferenceManager.RunInference(settings.samplerSettings.samplerCmd, 
        settings.samplerSettings.samplerArgs), inferenceManager, true)

    val calibrationTask = Task("calibration", List("inference"), 
      InferenceManager.WriteCalibrationData, inferenceManager)
    
    val reportingTask = Task("report", List("calibration"), Profiler.PrintReports, profiler, false)

    val terminationTask = Task("shutdown", List("report"), TaskManager.Shutdown, taskManager, false)

    val allTasks = extractionTasks ++ Seq(groundFactorGraphTask) ++
      List(inferenceTask, calibrationTask, reportingTask, terminationTask) 

    // Create a default pipeline that executes all tasks
    val defaultPipeline = Pipeline("_default", allTasks.map(_.id).toSet)

    // Figure out which pipeline to run
    val activePipeline = settings.pipelineSettings.activePipeline match {
      case Some(pipeline) => pipeline.copy(tasks = pipeline.tasks ++ 
        Set("inference_grounding", "inference", "calibration", "report", "shutdown"))
      case None => defaultPipeline
    }

    // We remove all tasks dependencies that are not in the pipeline
    val filteredTasks = allTasks.filter(t => activePipeline.tasks.contains(t.id)).map { task =>
      val newDependencies = task.dependencies.filter(activePipeline.tasks.contains(_))
      task.copy(dependencies=newDependencies)
    }

    log.info(s"Running pipeline=${activePipeline.id} with tasks=${filteredTasks.map(_.id)}")
    
    // Schedule all Tasks. 
    for (task <- filteredTasks) {
      taskManager ! TaskManager.AddTask(task)
    }

    // Wait for the system to shutdown
    system.awaitTermination()

    // Clean up resources
    Context.shutdown()
  }

}
