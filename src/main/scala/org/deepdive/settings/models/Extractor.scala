package org.deepdive.settings

/* Extractor specified in the settings */
case class Extractor(name:String, outputRelation: String, inputQuery: String, udf: String, dependencies: Set[String])