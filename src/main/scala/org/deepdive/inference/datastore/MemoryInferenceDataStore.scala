package org.deepdive.inference

import au.com.bytecode.opencsv.{CSVWriter, CSVReader}
import java.io.{File, FileWriter, FileReader}
import org.deepdive.calibration._
import org.deepdive.Logging
import org.deepdive.settings._
import scala.collection.mutable.{Map => MMap}
import scala.collection.JavaConversions._

trait MemoryInferenceDataStoreComponent extends InferenceDataStoreComponent{

  lazy val inferenceDataStore = new MemoryInferenceDataStore

  class MemoryInferenceDataStore extends InferenceDataStore with Logging {
    
    def init() = {
    }

    def groundFactorGraph(schema: Map[String, _ <: VariableDataType],
      factorDescs: Seq[FactorDesc], calibrationSettings: CalibrationSettings, 
      skipLearning: Boolean, weightTable: String, dbSettings: DbSettings, parallelGrounding: Boolean) : Unit = {

    }

    def BatchSize = None

    def writebackInferenceResult(variableSchema: Map[String, _ <: VariableDataType], 
      variableOutputFile: String, weightsOutputFile: String, parallelGrounding: Boolean, dbSettings: DbSettings) : Unit = {
    }

    def getCalibrationData(variable: String, dataType: VariableDataType, buckets: List[Bucket]) : Map[Bucket, BucketData] = {
      return Map[Bucket, BucketData]()
    }

  }
  
}