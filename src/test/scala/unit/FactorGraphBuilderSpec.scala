package org.deepdive.test.unit

import anorm._
import org.deepdive.datastore.PostgresDataStore
import org.scalatest._
import org.deepdive._
import org.deepdive.context._
import org.deepdive.context.{Factor => FactorDesc}
import org.deepdive.inference.{FactorGraphBuilder}
import akka.actor._
import akka.testkit.{TestActorRef, TestKit}

class FactorGraphBuilderSpec extends FunSpec {

  implicit val system = ActorSystem("Test")

  def prepareData() {
    PostgresDataStore.init("jdbc:postgresql://localhost/deepdive_test", "dennybritz", "")
    PostgresDataStore.withConnection { implicit conn =>
     SQL("drop schema if exists public cascade; create schema public;").execute()
     SQL("create table entities (id bigserial primary key, word_id integer)").execute()
     SQL("""create table parents(id bigserial primary key, entity1_id integer, 
        entity2_id integer, meta text)""").execute()
     SQL("insert into entities(word_id) VALUES (1), (2), (3), (4), (5), (6);").execute()
     SQL("""insert into parents(entity1_id, entity2_id, meta) 
        VALUES (1, 2, 'A'), (2, 3, 'B'), (1, 5, 'C');""").execute()
   }
  }


  describe("addVariableAndFactorsForRelation") {
    
    it("should work") {
      prepareData()
      
      val actor = TestActorRef[FactorGraphBuilder].underlyingActor

      // Add Factors and Variables for the entities relation
      val entityRelation = Relation("entities", Map("id" -> "Long", "word_id" -> "Integer"), Nil, None)
      val entityFactorDesc = FactorDesc("entitity", ImplyFactorFunction("id", Nil), UnknownFactorWeight(Nil))
      actor.addVariableAndFactorsForRelation(entityRelation, Option(entityFactorDesc))
      // Should have one variable and factor for each tuple
      assert(actor.factorStore.variables.size == 6)
      assert(actor.factorStore.factors.size == 6)
      actor.writeToDatabase("entities")

      // Add Factors and Variables for the parents relations
      val parentsRelation = Relation("parents",
        Map("id" -> "Long", "entity1_id" -> "Long", "entity2_id" -> "Long"),
        List(ForeignKey("parents", "entity1_id", "entities", "id"), 
          ForeignKey("parents", "entity2_id", "entities", "id")),
        None
      )
      val parentsFactorDesc = FactorDesc("parent", 
        ImplyFactorFunction("id", List("entity1_id", "entity2_id")), UnknownFactorWeight(List("entity1_id")))
      actor.addVariableAndFactorsForRelation(parentsRelation, Option(parentsFactorDesc))
      assert(actor.factorStore.variables.size == 9)
      assert(actor.factorStore.factors.size == 3)
      actor.writeToDatabase("parents")

      // TODO: Make sure the factors are correct

      
      // Make sure the dat ain the RDBMS is correct.
      PostgresDataStore.withConnection { implicit conn =>
        val factorCount = SQL("select count(*) as c from factors")().head[Long]("c")
        assert(factorCount == 9)
        val variableCount = SQL("select count(*) as c from variables")().head[Long]("c")
        assert(variableCount == 9)
        val weightCount = SQL("select count(*) as c from weights")().head[Long]("c")
        assert(weightCount == 3)
      }
    }
      
  }

}