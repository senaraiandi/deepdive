package org.deepdive.datastore

/* A postgresql database */
class PostgresDataStore extends DataStore {

  def hasRelationWithName(name: String) : Boolean = {
    throw new java.lang.UnsupportedOperationException("Not yet implemented.")
  }
  
  def createRelation(relation: Relation) {
    throw new java.lang.UnsupportedOperationException("Not yet implemented.")
  }

  def getSchema() : Array[Relation] = {
    throw new java.lang.UnsupportedOperationException("Not yet implemented.")
  }

}