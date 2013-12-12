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
    val connection = loadConnection(config)
    val relations = loadRelations(config)
    val etlTasks = loadEtlTasks(config)
    val extractors = loadExtractors(config)
    val factors = loadFactors(config)

    Settings(connection, relations, etlTasks, extractors, factors)
  }

  private def loadConnection(config: Config) : Connection = {
    Connection(
      config.getString("global.connection.host"),
      config.getInt("global.connection.port"),
      config.getString("global.connection.db"),
      config.getString("global.connection.user"),
      config.getString("global.connection.password")
    )
  }

  private def loadRelations(config: Config) : List[Relation] = {
    config.getObject("relations").keySet().map { relationName =>
      val relationConfig = config.getConfig(s"relations.$relationName")
      val schema = relationConfig.getObject("schema").unwrapped
      val foreignKeys = Try(relationConfig.getObject("fkeys").keySet().map { childAttr =>
        val Array(parentRelation, parentAttribute) = relationConfig.getString(s"fkeys.${childAttr}").split('.')
        ForeignKey(relationName, childAttr, parentRelation, parentAttribute)
      }).getOrElse(Nil).toList
      // We add "id" as a key so that it can be used as a variable. 
      // TODO: Rename foreign keys to something more appropriate
      val allKeys = foreignKeys :+ ForeignKey(relationName, "id", relationName, "id")
      // Evidence
      val evidence = Try(relationConfig.getString("query_field")).toOption
      Relation(relationName,schema.toMap.mapValues(_.toString), allKeys, evidence)
    }.toList
  }

  private def loadEtlTasks(config: Config) : List[EtlTask] = {
    Try(config.getObject("ingest").keySet().map { relationName =>
      val source = config.getString(s"ingest.$relationName.source")
      EtlTask(relationName, source)
    }.toList).getOrElse(Nil)
  }

  private def loadExtractors(config: Config) : List[Extractor] = {
    config.getObject("extractions").keySet().map { extractorName =>
      val extractorConfig = config.getConfig(s"extractions.$extractorName")
      val outputRelation = extractorConfig.getString("output_relation")
      val inputQuery = extractorConfig.getString(s"input")
      val udf = extractorConfig.getString(s"udf")
      Extractor(extractorName, outputRelation, inputQuery, udf)
    }.toList
  }

  private def loadFactors(config: Config): List[FactorDesc] = {
    Try(config.getObject("factors").keySet().map { factorName =>
      val factorConfig = config.getConfig(s"factors.$factorName")
      val factorInputQuery = factorConfig.getString("input_query")
      val factorFunction = FactorFunctionParser.parse(
        FactorFunctionParser.factorFunc, factorConfig.getString("function"))
      val factorWeight = FactorWeightParser.parse(
        FactorWeightParser.factorWeight, factorConfig.getString("weight"))
      FactorDesc(factorName, factorInputQuery, factorFunction.get, factorWeight.get)
    }.toList).getOrElse(Nil)
  }

}