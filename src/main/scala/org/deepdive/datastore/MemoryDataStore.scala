package org.deepdive.datastore

import org.deepdive.Logging
import scala.collection.mutable.{Map => MMap, ArrayBuffer}
import play.api.libs.json._

/* Stores Extraction Results */
trait MemoryDataStoreComponent extends JdbcDataStoreComponent{
  val dataStore = new MemoryDataStore
}

class MemoryDataStore extends JdbcDataStore with Logging {
    
    def BatchSize = 100000
    
    val data = MMap[String, ArrayBuffer[JsObject]]()

    override def init() = {
      data.clear()
    }

    override def queryAsJson[A](query: String, batchSize: Option[Int] = None)
      (block: Iterator[JsObject] => A) : A = {
      block(data.get(query).map(_.toList).getOrElse(Nil).iterator)
    }
    
    override def queryAsMap[A](query: String, batchSize: Option[Int] = None)
      (block: Iterator[Map[String, Any]] => A) : A = {
      queryAsJson(query) { iter => 
        block(iter.map(_.value.toMap.mapValues {
          case JsNull => null
          case JsString(x) => x
          case JsNumber(x) => x
          case JsBoolean(x) => x
          case _ => null
        }))
      }
    }

    override def queryUpdate(query: String) {}
    
    override def addBatch(result: Iterator[JsObject], outputRelation: String) : Unit = {
      //TODO: Use parallel collection
      data.synchronized {
        data.get(outputRelation) match {
          case Some(rows) => rows ++= result.toSeq
          case None => data += Tuple2(outputRelation, ArrayBuffer(result.toList: _*))
        }
      }
    }

}
