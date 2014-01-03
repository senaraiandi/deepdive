package org.deepdive.settings

import com.typesafe.config._
import scala.collection.JavaConversions._
import scala.util.Try

object SettingsParser {

   def loadFromConfig(rootConfig: Config) : Settings = {
    // Validations makes sure that the supplied config includes all the required settings.
    rootConfig.checkValid(ConfigFactory.defaultReference(), "deepdive")
    val config = rootConfig.getConfig("deepdive")

    // Connection settings
    val schemaSettings = loadSchemaSettings(config)
    val extractors = loadExtractionSettings(config)
    val inferenceSettings = loadInferenceSettings(config)
    val calibrationSettings = loadCalibrationSettings(config)
    val samplerSettings = loadSamplerSettings(config)

    Settings(schemaSettings, extractors, inferenceSettings, 
      calibrationSettings, samplerSettings)
  }

  private def loadSchemaSettings(config: Config) : SchemaSettings = {
    Try(config.getConfig("schema")).map { schemaConfig =>
      val variableConfig = schemaConfig.getConfig("variables")
      val relations = variableConfig.root.keySet.toList
      val relationsWithConfig = relations.zip(relations.map(variableConfig.getConfig))
      val variableMap = relationsWithConfig.flatMap { case(relation, relationConf) =>
        relationConf.root.keySet.map { attributeName =>
          Tuple2(s"${relation}.${attributeName}", 
            relationConf.getString(attributeName))
        }
      }.toMap
      SchemaSettings(variableMap)
    }.getOrElse(SchemaSettings(Map()))
  }

  private def loadExtractionSettings(config: Config) : ExtractionSettings = {
    val extractionConfig = config.getConfig("extraction")
    val initialVariableId = Try(extractionConfig.getLong("initial_vid")).getOrElse(0l)
    val extractors = extractionConfig.getObject("extractors").keySet().map { extractorName =>
      val extractorConfig = extractionConfig.getConfig(s"extractors.$extractorName")
      val outputRelation = extractorConfig.getString("output_relation")
      val inputQuery = extractorConfig.getString(s"input")
      val udf = extractorConfig.getString(s"udf")
      val parallelism = Try(extractorConfig.getInt(s"parallelism")).getOrElse(1)
      val inputBatchSize = Try(extractorConfig.getInt(s"input_batch_size")).getOrElse(10000)
      val outputBatchSize = Try(extractorConfig.getInt(s"output_batch_size")).getOrElse(50000)
      val dependencies = Try(extractorConfig.getStringList("dependencies").toSet).getOrElse(Set())
      Extractor(extractorName, outputRelation, inputQuery, udf, parallelism, 
        inputBatchSize, outputBatchSize, dependencies)
    }.toList
    ExtractionSettings(initialVariableId, extractors)
  }

  private def loadInferenceSettings(config: Config): InferenceSettings = {
    Try(config.getConfig("inference")).map { inferenceConfig =>
      val batchSize = Try(inferenceConfig.getInt("batch_size")).toOption
      val factors = Try(inferenceConfig.getObject("factors").keySet().map { factorName =>
        val factorConfig = inferenceConfig.getConfig(s"factors.$factorName")
        val factorInputQuery = factorConfig.getString("input_query")
        val factorFunction = FactorFunctionParser.parse(
          FactorFunctionParser.factorFunc, factorConfig.getString("function"))
        val factorWeight = FactorWeightParser.parse(
          FactorWeightParser.factorWeight, factorConfig.getString("weight"))
        val factorWeightPrefix = Try(factorConfig.getString("weightPrefix")).getOrElse(factorName)
        FactorDesc(factorName, factorInputQuery, factorFunction.get, 
          factorWeight.get, factorWeightPrefix)
      }.toList).getOrElse(Nil)
      InferenceSettings(factors, batchSize)
    }.getOrElse(InferenceSettings(Nil, None))
  }

  private def loadCalibrationSettings(config: Config) : CalibrationSettings = {
    Try(config.getConfig("calibration")).map { calibrationConfig =>
      val holdoutFraction = Try(calibrationConfig.getDouble("holdout_fraction")).getOrElse(0.0)
      CalibrationSettings(holdoutFraction)
    }.getOrElse(CalibrationSettings(0.0))
  }

  private def loadSamplerSettings(config: Config) : SamplerSettings = {
    val samplingConfig = config.getConfig("sampler")
    val javaArgs = Try(samplingConfig.getString("java_args")).getOrElse("")
    val samplerArgs = samplingConfig.getString("sampler_args")
    SamplerSettings(javaArgs, samplerArgs)
  }

}