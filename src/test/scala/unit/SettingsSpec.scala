import com.typesafe.config._
import org.deepdive.context._
import org.scalatest._

class SettingsSpec extends FunSpec {

  describe("Settings") {

    it("should parse a simple configuration file") {
      Settings.loadFromConfig(ConfigFactory.load("simple_config"))
      val settings = Settings.get()
      
      assert(settings.connection == Connection("localhost", 5432, "deepdive_test", 
        "root", "password"))
      
      assert(settings.relations == List(
        Relation("documents", Map[String,String]("varid" -> "Integer", 
          "text" -> "Text", "meta" -> "Text"), Nil),
        Relation("entities", Map[String,String]("varid" -> "Integer", 
          "document_id" -> "Integer", "name" -> "String", "meta" -> "Text"), Nil)
      ))

      assert(settings.etlTasks == List(
        EtlTask("documents", "data/documents.tsv")
      ))

    }

  }

}