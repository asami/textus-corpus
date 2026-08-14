package org.simplemodeling.textus.corpus.evaluation

import scala.collection.mutable

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, OperationEvaluationDeliveryResult, OperationEvaluationDeliveryStatus, OperationEvaluationFact, OperationEvaluationFactId, OperationEvaluationLimitation, OperationEvaluationLimitationKind, OperationEvaluationSinkIdentity, OperationEvaluationStartFact, OperationEvaluationTerminalFact}
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiSelection}
import org.goldenport.cncf.spi.evaluation.CorpusEvaluationSink
import org.simplemodeling.textus.corpus.CorpusComponent

/*
 * Bounded, non-persistent Corpus evaluation sink for development and offline
 * verification. Production candidate review and retention require a separate
 * persistent provider.
 *
 * @since   Jul. 23, 2026
 *  version Jul. 24, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class OfflineCorpusEvaluationSinkAdapter private (
  sinkidentity: OperationEvaluationSinkIdentity,
  maximumfactcount: Int
) extends CorpusEvaluationSink {
  private val _facts = mutable.LinkedHashMap.empty[OperationEvaluationFactId, OperationEvaluationFact]

  override val sinkIdentityOption: Option[OperationEvaluationSinkIdentity] = Some(sinkidentity)

  def sinkIdentity: OperationEvaluationSinkIdentity = sinkidentity
  def maximumFactCount: Int = maximumfactcount
  def facts: Vector[OperationEvaluationFact] = synchronized(_facts.values.toVector)

  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  private def _record(fact: OperationEvaluationFact): Consequence[OperationEvaluationDeliveryResult] = synchronized {
    val key = fact.id
    _facts.get(key) match {
      case Some(current) if current == fact =>
        _result(fact, OperationEvaluationDeliveryStatus.Delivered)
      case Some(_) =>
        Consequence.stateConflict(s"operation evaluation fact id already has different content: ${key.toString}")
      case None if _facts.size >= maximumfactcount =>
        _result(
          fact,
          OperationEvaluationDeliveryStatus.Limited,
          Vector(OperationEvaluationLimitation(OperationEvaluationLimitationKind.Saturated))
        )
      case None =>
        _facts.put(key, fact)
        _result(fact, OperationEvaluationDeliveryStatus.Delivered)
    }
  }

  private def _result(
    fact: OperationEvaluationFact,
    status: OperationEvaluationDeliveryStatus,
    limitations: Vector[OperationEvaluationLimitation] = Vector.empty
  ): Consequence[OperationEvaluationDeliveryResult] =
    OperationEvaluationDeliveryResult.createC(
      fact.id,
      sinkidentity,
      status,
      limitations,
      fact.confidentiality
    )
}

object OfflineCorpusEvaluationSinkAdapter {
  val DEFAULT_MAXIMUM_FACT_COUNT: Int = 4096
  val PROVIDER_COMPONENT: String = CorpusComponent.name
  val PROVIDER_INSTANCE: String = "offline"

  def createC(
    socketcomponent: String,
    maximumfactcount: Int = DEFAULT_MAXIMUM_FACT_COUNT
  ): Consequence[OfflineCorpusEvaluationSinkAdapter] =
    if (maximumfactcount <= 0)
      Consequence.argumentInvalid("maximumFactCount", "positive fact capacity", maximumfactcount)
    else
      OperationEvaluationSinkIdentity
        .createC(
          CorpusEvaluationSink.CONTRACT_NAME,
          socketcomponent,
          PROVIDER_COMPONENT,
          Some(PROVIDER_INSTANCE)
        )
        .map(new OfflineCorpusEvaluationSinkAdapter(_, maximumfactcount))
}

final case class OfflineCorpusEvaluationSinkProvider(
  maximumfactcount: Int = OfflineCorpusEvaluationSinkAdapter.DEFAULT_MAXIMUM_FACT_COUNT
) extends SpiProvider[CorpusEvaluationSink] {
  def supports(
    contract: SpiContract[CorpusEvaluationSink],
    selection: SpiSelection
  )(using ExecutionContext): Boolean =
    contract.name == CorpusEvaluationSink.CONTRACT_NAME &&
      contract.runtimeClass == classOf[CorpusEvaluationSink] &&
      selection.mode.forall(_ == "offline")

  def provide(
    contract: SpiContract[CorpusEvaluationSink],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[CorpusEvaluationSink] =
    OfflineCorpusEvaluationSinkAdapter.createC("offline-consumer", maximumfactcount)
}
