package org.deepdive

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config._
import java.io.File
import org.deepdive.settings.{Settings}
import org.deepdive.datastore.{PostgresDataStore}
import org.deepdive.extraction.{ExtractionManager, ExtractionTask, ExtractionTaskResult}
import org.deepdive.inference.{InferenceManager, FactorGraphBuilder, FactorTask}
import org.deepdive.profiling._
import org.deepdive.calibration._
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.util.{Try, Success, Failure}

object Pipeline extends Logging {

  lazy val VARIABLES_DUMP_FILE = new File("target/variables.tsv")
  lazy val FACTORS_DUMP_FILE = new File("target/factors.tsv")
  lazy val WEIGHTS_DUMP_FILE = new File("target/weights.tsv")
  lazy val SAMPLING_OUTPUT_FILE = new File("target/inference_result.out")

  def run(config: Config) {

    // Get the actor system
    val system = Context.system

    // Load Settings
    Context.settings = Settings.loadFromConfig(config)    

    // Initialize the data store
    PostgresDataStore.init(Context.settings.connection.url, Context.settings.connection.user, 
      Context.settings.connection.password)

    implicit val timeout = Timeout(1337 hours)
    implicit val ec = system.dispatcher

    // Start actors
    val profiler = system.actorOf(Profiler.props, "profiler")
    val taskManager = system.actorOf(TaskManager.props, "taskManager")
    val inferenceManager = system.actorOf(InferenceManager.props(
      Context.settings.schemaSettings.variables), "inferenceManager")
    val extractionManager = system.actorOf(ExtractionManager.props, "extractionManager")
    
    // Build tasks for extractors
    val extractionTasks = for {
      extractor <- Context.settings.extractionSettings.extractors
      extractionTask = ExtractionTask(extractor)
    } yield Task(extractor.name, extractor.dependencies.toList, extractionTask, extractionManager)

    // Build task to construct the factor graph
    val factorTasks = for {
       factor <- Context.settings.factors
       factorTask = FactorTask(factor, Context.settings.calibrationSettings.holdoutFraction)
       relationDependencies = factor.func.relations
       extractors = Context.settings.extractionSettings.extractors
       extractionDeps = extractors.filter ( x => relationDependencies.contains(x.outputRelation) )
       taskDeps = extractionDeps.map(_.name)
    } yield Task(factor.name, taskDeps, factorTask, inferenceManager)

    // Build a task to dump the factor graph
    val dumpFactorGraphTask = Task(
      "dump_factor_graph", factorTasks.map(_.id), 
      InferenceManager.DumpFactorGraph(VARIABLES_DUMP_FILE.getCanonicalPath, 
        FACTORS_DUMP_FILE.getCanonicalPath, WEIGHTS_DUMP_FILE.getCanonicalPath), 
      inferenceManager)

    // Buld task to run inference
    val samplerCmd = Seq("java", Context.settings.samplerSettings.javaArgs, 
      "-jar", "lib/gibbs_sampling-assembly-0.1.jar", 
      "--variables", VARIABLES_DUMP_FILE.getCanonicalPath, 
      "--factors", FACTORS_DUMP_FILE.getCanonicalPath, 
      "--weights", WEIGHTS_DUMP_FILE.getCanonicalPath,
      "--output", SAMPLING_OUTPUT_FILE.getCanonicalPath) ++ Context.settings.samplerSettings.samplerArgs.split(" ")
    val samplerTask = Task("sampling", List("dump_factor_graph"), 
      InferenceManager.RunSampler(samplerCmd), inferenceManager)

    val writebackTask = Task("writeback_inference_result", List("sampling"),
      InferenceManager.WriteInferenceResult(SAMPLING_OUTPUT_FILE.getCanonicalPath), inferenceManager)

    val calibrationTask = Task("calibration_plots", List("writeback_inference_result"), 
      InferenceManager.WriteCalibrationData("target/calibration_data/counts", 
        "target/calibration_data/precision"), inferenceManager)
    
    val reportingTask = Task("report", List("calibration_plots"), Profiler.Report, profiler)

    val terminationTask = Task("shutdown", List("report"), "shutdown", taskManager)

    val allTasks = extractionTasks ++ factorTasks ++ 
      List(dumpFactorGraphTask, samplerTask, writebackTask, calibrationTask, 
        reportingTask, terminationTask) 

    // Schedule all Tasks. 
    allTasks.foreach( task => taskManager ! TaskManager.AddTask(task) )

    // Wait for the system to shutdown
    system.awaitTermination()

    // Clean up resources
    Context.shutdown()
  }

}
