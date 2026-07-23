package org.simplemodeling.textus.corpus.evaluation

import java.time.{Duration, Instant}

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, CorpusCaseReference, CorpusEvaluationCorrelation, CorpusRevisionReference, ExperimentArmReference, ExperimentEvaluationCorrelation, ExperimentReference, ExperimentRunReference, OperationEvaluationAttemptId, OperationEvaluationCorrelation, OperationEvaluationDeliveryStatus, OperationEvaluationExecutionId, OperationEvaluationFactId, OperationEvaluationFactSource, OperationEvaluationLabel, OperationEvaluationLimitationKind, OperationEvaluationOperationIdentity, OperationEvaluationOutcome, OperationEvaluationStartFact, OperationEvaluationTerminalFact, OperationEvaluationText}
import org.goldenport.cncf.spi.{SpiResolver, SpiSelection}
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSink, CorpusEvaluationSinkSocket}
import org.goldenport.schema.DataConfidentiality
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.corpus.impl.CorpusPrimaryComponent

/*
 * @since   Jul. 23, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class OfflineCorpusEvaluationSinkAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "OfflineCorpusEvaluationSinkAdapter" should {
    "retain ordered framework facts and a corpus candidate without changing their evidence" in {
      Given("one bounded offline Corpus sink and a corpus-case evaluation correlation")
      given ExecutionContext = ExecutionContext.create()
      val sink = _success(OfflineCorpusEvaluationSinkAdapter.createC("offline-spec", 8))
      val correlation = _correlation("ordered")
      val start = _start_fact("ordered-start", correlation)
      val terminal = _terminal_fact("ordered-terminal", correlation)
      val candidate = _candidate_fact("ordered-candidate", correlation, "candidate accepted")

      When("automatic and application-authored facts are delivered in evaluation order")
      val results = Vector(
        _success(sink.recordStart(start)),
        _success(sink.recordTerminal(terminal)),
        _success(sink.submitCandidate(candidate))
      )

      Then("all facts are delivered and retain ordering, source, confidentiality, and correlation")
      results.map(_.status) shouldBe Vector.fill(3)(OperationEvaluationDeliveryStatus.Delivered)
      sink.facts.map(_.id) shouldBe Vector(start.id, terminal.id, candidate.id)
      sink.facts.map(_.source) shouldBe Vector(
        OperationEvaluationFactSource.Framework,
        OperationEvaluationFactSource.Framework,
        OperationEvaluationFactSource.Application
      )
      sink.facts.map(_.confidentiality) shouldBe Vector.fill(3)(DataConfidentiality.Internal)
      sink.facts.map(_.correlation.executionId).distinct shouldBe Vector(correlation.executionId)
      sink.facts.map(_.correlation.attemptId).distinct shouldBe Vector(correlation.attemptId)
      candidate.correlation.corpus.flatMap(_.caseReference).map(_.print) shouldBe Some("case-17")
    }

    "treat exact fact redelivery as idempotent and reject replacement content" in {
      Given("one accepted candidate fact")
      given ExecutionContext = ExecutionContext.create()
      val sink = _success(OfflineCorpusEvaluationSinkAdapter.createC("offline-spec", 4))
      val correlation = _correlation("idempotent")
      val original = _candidate_fact("stable-id", correlation, "original")
      _success(sink.submitCandidate(original))

      When("the exact fact is retried and the same id is reused for different content")
      val retry = _success(sink.submitCandidate(original))
      val replacement = _candidate_fact("stable-id", correlation, "replacement")
      val conflict = sink.submitCandidate(replacement)

      Then("the retry is delivered once and replacement content fails deterministically")
      retry.status shouldBe OperationEvaluationDeliveryStatus.Delivered
      sink.facts shouldBe Vector(original)
      conflict.isFaillure shouldBe true
    }

    "report saturation without retaining facts beyond its bound" in {
      Given("an offline sink whose capacity is one fact")
      given ExecutionContext = ExecutionContext.create()
      val sink = _success(OfflineCorpusEvaluationSinkAdapter.createC("offline-spec", 1))
      val correlation = _correlation("bounded")
      val first = _start_fact("bounded-first", correlation)
      val second = _terminal_fact("bounded-second", correlation)
      _success(sink.recordStart(first))

      When("another fact is delivered after capacity is reached")
      val result = _success(sink.recordTerminal(second))

      Then("delivery is limited with the standard saturated limitation")
      result.status shouldBe OperationEvaluationDeliveryStatus.Limited
      result.limitations.map(_.kind) shouldBe Vector(OperationEvaluationLimitationKind.Saturated)
      sink.facts shouldBe Vector(first)

      And("the same bounded behavior holds across generated positive capacities")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), Prop.forAll(Gen.choose(1, 24)) { capacity =>
        val generated = _success(OfflineCorpusEvaluationSinkAdapter.createC("generated-spec", capacity))
        val facts = Vector.tabulate(capacity)(index => _start_fact(s"generated-$capacity-$index", correlation))
        val delivered = facts.map(generated.recordStart)
        val overflow = _success(generated.recordTerminal(_terminal_fact(s"overflow-$capacity", correlation)))
        delivered.forall(_.toOption.exists(_.status == OperationEvaluationDeliveryStatus.Delivered)) &&
          generated.facts == facts &&
          overflow.status == OperationEvaluationDeliveryStatus.Limited
      })
      checked.passed shouldBe true
    }

    "resolve through the standard Corpus evaluation SPI provider contract" in {
      Given("the Textus-owned offline provider")
      given ExecutionContext = ExecutionContext.create()
      val providercomponent = new CorpusPrimaryComponent()
      val consumer = CorpusConsumerComponent(SpiSelection(mode = Some("offline")))

      When("the CNCF SPI contract selects and materializes the provider")
      val resolution = SpiResolver.resolve(Vector(providercomponent, consumer))
      val sink = consumer.corpusEvaluationSink

      Then("the materialized service is the bounded Textus Corpus adapter")
      resolution shouldBe a[Consequence.Success[_]]
      consumer.isSpiInstalled shouldBe true
      sink.sinkIdentityOption.flatMap(_.toRecord.getString("contract")) shouldBe Some("corpus-evaluation-sink")
      sink.sinkIdentityOption.flatMap(_.toRecord.getString("socketComponent")) shouldBe Some("corpusconsumercomponent")

      And("an absent mode retains the offline development default")
      val defaultconsumer = CorpusConsumerComponent(SpiSelection())
      val defaultresolution = SpiResolver.resolve(Vector(providercomponent, defaultconsumer))
      defaultresolution shouldBe a[Consequence.Success[_]]
      defaultconsumer.isSpiInstalled shouldBe true

      And("an explicit non-offline mode leaves the optional socket uninstalled")
      val incompatible = CorpusConsumerComponent(SpiSelection(mode = Some("production")))
      val rejected = SpiResolver.resolve(Vector(providercomponent, incompatible))
      rejected shouldBe a[Consequence.Success[_]]
      incompatible.isSpiInstalled shouldBe false
    }
  }

  private val _instant = Instant.parse("2026-07-23T00:00:00Z")

  private final case class CorpusConsumerComponent(
    selection: SpiSelection
  ) extends Component with CorpusEvaluationSinkSocket {
    override def spiSelection: SpiSelection = selection
  }

  private def _correlation(entropy: String): OperationEvaluationCorrelation = {
    val revision = _success(CorpusRevisionReference.parseC("revision-20260723"))
    val corpuscase = _success(CorpusCaseReference.parseC("case-17"))
    val experiment = _success(ExperimentReference.parseC("experiment-9"))
    val arm = _success(ExperimentArmReference.parseC("arm-control"))
    val run = _success(ExperimentRunReference.parseC("run-3"))
    OperationEvaluationCorrelation(
      OperationEvaluationExecutionId("spec", "execution", Some(_instant), Some(entropy)),
      OperationEvaluationAttemptId("spec", "attempt", Some(_instant), Some(entropy)),
      _success(OperationEvaluationOperationIdentity.createC("sample", "evaluation", "evaluate")),
      corpus = Some(CorpusEvaluationCorrelation.create(revision, Some(corpuscase))),
      experiment = Some(_success(ExperimentEvaluationCorrelation.createC(
        experiment,
        Some(arm),
        Some(run),
        Some(revision)
      )))
    )
  }

  private def _start_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): OperationEvaluationStartFact =
    OperationEvaluationStartFact.create(_fact_id(entropy), correlation, _instant)

  private def _terminal_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation
  ): OperationEvaluationTerminalFact =
    _success(OperationEvaluationTerminalFact.createC(
      _fact_id(entropy),
      correlation,
      _instant.plusMillis(25),
      OperationEvaluationOutcome.Success,
      Duration.ofMillis(25)
    ))

  private def _candidate_fact(
    entropy: String,
    correlation: OperationEvaluationCorrelation,
    summary: String
  ): CorpusCandidateFact =
    _success(CorpusCandidateFact.createC(
      _fact_id(entropy),
      correlation,
      _instant.plusMillis(10),
      Some(_success(OperationEvaluationText.parseC(summary))),
      Vector(_success(OperationEvaluationLabel.createC("decision", "review")))
    ))

  private def _fact_id(entropy: String): OperationEvaluationFactId =
    OperationEvaluationFactId("spec", "fact", Some(_instant), Some(entropy))

  private def _success[A](consequence: Consequence[A]): A =
    consequence.toOption.getOrElse(fail(consequence.toString))
}
