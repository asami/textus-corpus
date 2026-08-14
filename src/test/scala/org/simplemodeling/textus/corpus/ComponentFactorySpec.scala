package org.simplemodeling.textus.corpus

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.corpus.impl.{ComponentFactory, CorpusPrimaryComponent}

/*
 * @since   Jul. 21, 2026
 *  version Jul. 27, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Textus Corpus" should {
    "publish only the semantic registry service" in {
      Given("a primary Corpus component")
      val component = _component()

      When("its public protocol is inspected")
      val services = component.protocol.services.services

      Then("generic aggregate, view, and entity CRUD services are absent")
      services.map(_.name) should contain ("CorpusRegistry")
      services.map(_.name) should not contain allOf ("aggregate", "view", "entity")
      services.find(_.name == "CorpusRegistry").toVector
        .flatMap(_.operations.operations.toVector).map(_.name) should contain theSameElementsAs Vector(
        "publishCorpus",
        "registerCorpusCase",
        "searchCorpusRevisions",
        "getCorpusRevision",
        "getCorpusCase",
        "listCorpusCases"
      )
    }

    "retain immutable revisions and cases through idempotent semantic commands" in {
      Given("an assembled Corpus component with an in-memory entity store")
      val component = _component()
      given ExecutionContext = component.logic.executionContext()

      When("a revision and case are published twice with the same content")
      val firstrevision = _record(_operation(component, "publishCorpus",
        "corpusKey" -> "sanpomap-phase-2",
        "revision" -> "1",
        "name" -> "Sanpomap Phase 2",
        "kind" -> "evaluation",
        "description" -> "Representative production cases",
        "sourceDigest" -> "sha256:revision-1"
      ))
      val secondrevision = _record(_operation(component, "publishCorpus",
        "corpusKey" -> "sanpomap-phase-2",
        "revision" -> "1",
        "name" -> "Sanpomap Phase 2",
        "kind" -> "evaluation",
        "description" -> "Representative production cases",
        "sourceDigest" -> "sha256:revision-1"
      ))
      val revisionid = firstrevision.getString("id").getOrElse(fail("revision id missing"))
      val firstcase = _record(_operation(component, "registerCorpusCase",
        "corpusRevisionId" -> revisionid,
        "caseKey" -> "scenario-simple",
        "applicationPurpose" -> "sanpomap-scenario-generation",
        "difficulty" -> "simple",
        "fixtureReference" -> "corpus://sanpomap/scenario-simple/input",
        "expectedEvidenceReference" -> "corpus://sanpomap/scenario-simple/expected"
      ))
      val secondcase = _record(_operation(component, "registerCorpusCase",
        "corpusRevisionId" -> revisionid,
        "caseKey" -> "scenario-simple",
        "applicationPurpose" -> "sanpomap-scenario-generation",
        "difficulty" -> "simple",
        "fixtureReference" -> "corpus://sanpomap/scenario-simple/input",
        "expectedEvidenceReference" -> "corpus://sanpomap/scenario-simple/expected"
      ))

      Then("retries return the existing immutable identifiers")
      secondrevision.getString("id") shouldBe firstrevision.getString("id")
      secondcase.getString("id") shouldBe firstcase.getString("id")

      And("registry queries return the retained revision and case")
      val revisions = _items(_record(_operation(component, "searchCorpusRevisions",
        "corpusKey" -> "sanpomap-phase-2",
        "revision" -> "1"
      )))
      val cases = _items(_record(_operation(component, "listCorpusCases",
        "corpusRevisionId" -> revisionid,
        "applicationPurpose" -> "sanpomap-scenario-generation"
      )))
      val revision = _record(_operation(component, "getCorpusRevision",
        "corpusRevisionId" -> revisionid
      )).getRecord("item").getOrElse(fail("revision item missing"))
      val caseid = firstcase.getString("id").getOrElse(fail("case id missing"))
      val corpuscase = _record(_operation(component, "getCorpusCase",
        "corpusCaseId" -> caseid
      )).getRecord("item").getOrElse(fail("case item missing"))
      revisions.map(_.getString("corpusKey")) shouldBe Vector(Some("sanpomap-phase-2"))
      cases.map(_.getString("caseKey")) shouldBe Vector(Some("scenario-simple"))
      revision.getString("id") shouldBe Some(revisionid)
      corpuscase.getString("id") shouldBe Some(caseid)
      corpuscase.getString("corpusRevisionId") shouldBe Some(revisionid)
    }

    "reject replacement content and cases without a published parent revision" in {
      Given("one published immutable corpus revision")
      val component = _component()
      given ExecutionContext = component.logic.executionContext()
      val revision = _record(_operation(component, "publishCorpus",
        "corpusKey" -> "sanpomap-phase-2",
        "revision" -> "1",
        "name" -> "Sanpomap Phase 2",
        "kind" -> "evaluation",
        "sourceDigest" -> "sha256:revision-1"
      ))
      val revisionid = revision.getString("id").getOrElse(fail("revision id missing"))

      When("the same semantic revision is submitted with different content")
      val replacement = _operation(component, "publishCorpus",
        "corpusKey" -> "sanpomap-phase-2",
        "revision" -> "1",
        "name" -> "Sanpomap Phase 2 changed",
        "kind" -> "evaluation",
        "sourceDigest" -> "sha256:revision-1-changed"
      )

      Then("the replacement is rejected as an immutable conflict")
      replacement shouldBe a[Consequence.Failure[_]]
      _failure_message(replacement) should include ("immutable")

      When("a case targets an unknown revision identifier")
      val unknownrevisionid = revisionid.dropRight(1) + (if (revisionid.endsWith("0")) "1" else "0")
      val orphan = _operation(component, "registerCorpusCase",
        "corpusRevisionId" -> unknownrevisionid,
        "caseKey" -> "orphan",
        "applicationPurpose" -> "sanpomap-scenario-generation",
        "fixtureReference" -> "corpus://sanpomap/orphan/input"
      )

      Then("the orphan case is rejected before persistence")
      orphan shouldBe a[Consequence.Failure[_]]
      _failure_message(orphan) should include ("does not exist")
    }
  }

  private def _component(): CorpusPrimaryComponent = {
    val base = ExecutionContext.create()
    val datastorespace = new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())
    val entitystorespace = EntityStoreSpace.create(
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )
    val scope = ScopeContext.Instance(ScopeContext.Core(
      kind = ScopeKind.Subsystem,
      name = "textus-corpus-spec",
      parent = None,
      observabilityContext = base.observability,
      httpDriverOption = None,
      datastore = Some(DataStoreContext(datastorespace)),
      entitystore = Some(EntityStoreContext(entitystorespace))
    ))
    val subsystem = RuntimeBindingAdmissionFixture.admit(
      new Subsystem(
        name = "textus-corpus-spec",
        scopecontext = Some(scope),
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
    )
    val bundle = new ComponentFactory().create(ComponentCreate(subsystem, ComponentOrigin.Main))
    subsystem.add(bundle.participants)
    bundle.primary.asInstanceOf[CorpusPrimaryComponent]
  }

  private def _operation(
    component: CorpusPrimaryComponent,
    operation: String,
    properties: (String, String)*
  )(using context: ExecutionContext): Consequence[OperationResponse] = {
    val request = Request.of(
      component = CorpusComponent.name,
      service = "CorpusRegistry",
      operation = operation,
      properties = properties.map { case (name, value) => Property(name, value, None) }.toList
    )
    component.logic.makeOperationRequest(request) match {
      case Consequence.Success(action) =>
        component.logic.executeAction(action.asInstanceOf[Action], context)
      case Consequence.Failure(conclusion) =>
        Consequence.Failure(conclusion)
    }
  }

  private def _record(result: Consequence[OperationResponse]): Record =
    result match {
      case Consequence.Success(OperationResponse.RecordResponse(record)) => record
      case Consequence.Success(other) => fail(s"expected record response but got $other")
      case Consequence.Failure(conclusion) => fail(s"operation failed: ${conclusion.show}")
    }

  private def _items(record: Record): Vector[Record] =
    record.getVector("items").getOrElse(Vector.empty).collect { case item: Record => item }

  private def _failure_message(result: Consequence[OperationResponse]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show
      case Consequence.Success(response) => fail(s"expected failure but got $response")
    }
}
