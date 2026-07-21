package org.simplemodeling.textus.corpus.impl

import cats.implicits.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.corpus.CorpusComponent
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityQuery, EntitySearchScope}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.textus.corpus.entity.{CorpusCase, CorpusRevision}
import org.simplemodeling.textus.corpus.entity.create.{CorpusCase as CorpusCaseCreate, CorpusRevision as CorpusRevisionCreate}

/*
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    CorpusPrimaryFactory

  override def componentletFactories: Vector[Component.ComponentletFactory] =
    Vector.empty
}

abstract class CorpusParticipantFactoryBase extends CorpusComponent.Factory {
  protected final val shared_services =
    Vector(
      CorpusComponent.CorpusRegistryService
    )

  protected final def component_core(
    name: String,
    componentid: ComponentId
  ): Component.Core =
    spec_create(name, componentid, shared_services)

  override val CorpusRegistry: CorpusComponent.CorpusRegistryServiceFactory = DefaultCorpusRegistryServiceFactory()
  override val aggregate: CorpusComponent.AggregateServiceFactory = AggregateServiceFactoryImpl()
  override val view: CorpusComponent.ViewServiceFactory = ViewServiceFactoryImpl()
  override val entity: CorpusComponent.EntityServiceFactory = DefaultEntityServiceFactory()
}

final class CorpusPrimaryComponent extends CorpusComponent {
  override def mcpReadyServices: Set[String] =
    Set.empty
}

object CorpusPrimaryFactory extends CorpusParticipantFactoryBase with Component.PrimaryComponentFactory {
  override protected def create_Component(params: ComponentCreate): Component =
    new CorpusPrimaryComponent()

  override protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    component_core(CorpusComponent.name, CorpusComponent.componentId)
}

final class DefaultCorpusRegistryServiceFactory extends CorpusComponent.CorpusRegistryServiceFactory {
  import CorpusComponent.CorpusRegistryService.*

  override def createPublishCorpusActionCall(
    core: ActionCall.Core,
    action: PublishCorpus
  ): PublishCorpusActionCall =
    PublishCorpusActionCallImpl(core, action)

  override def createRegisterCorpusCaseActionCall(
    core: ActionCall.Core,
    action: RegisterCorpusCase
  ): RegisterCorpusCaseActionCall =
    RegisterCorpusCaseActionCallImpl(core, action)

  override def createSearchCorpusRevisionsActionCall(
    core: ActionCall.Core,
    action: SearchCorpusRevisions
  ): SearchCorpusRevisionsActionCall =
    SearchCorpusRevisionsActionCallImpl(core, action)

  override def createGetCorpusRevisionActionCall(
    core: ActionCall.Core,
    action: GetCorpusRevision
  ): GetCorpusRevisionActionCall =
    GetCorpusRevisionActionCallImpl(core, action)

  override def createGetCorpusCaseActionCall(
    core: ActionCall.Core,
    action: GetCorpusCase
  ): GetCorpusCaseActionCall =
    GetCorpusCaseActionCallImpl(core, action)

  override def createListCorpusCasesActionCall(
    core: ActionCall.Core,
    action: ListCorpusCases
  ): ListCorpusCasesActionCall =
    ListCorpusCasesActionCallImpl(core, action)
  }

private trait CorpusRegistryActionSupport {
  self: org.goldenport.cncf.action.FunctionalActionCall =>

  protected final def all_revisions: ExecUowM[Vector[CorpusRevision]] =
    entity_search_internal[CorpusRevision](EntityQuery(
      org.simplemodeling.textus.corpus.entity.query.CorpusRevision.collectionId,
      Query.fromRecord(Record.empty),
      EntitySearchScope.Store
    )).map(_.data.toVector)

  protected final def all_cases: ExecUowM[Vector[CorpusCase]] =
    entity_search_internal[CorpusCase](EntityQuery(
      org.simplemodeling.textus.corpus.entity.query.CorpusCase.collectionId,
      Query.fromRecord(Record.empty),
      EntitySearchScope.Store
    )).map(_.data.toVector)

  protected final def required_entity_id(record: Record, name: String): Consequence[EntityId] =
    record.getAs[EntityId](name)
      .map(Consequence.success)
      .getOrElse(Consequence.failRecordNotFound(name, record))

  protected final def page[A](items: Vector[A], record: Record): Vector[A] = {
    val offset = record.getInt("offset").getOrElse(0).max(0)
    val limit = record.getInt("limit").getOrElse(100).max(0)
    items.drop(offset).take(limit)
  }
}

private final case class PublishCorpusActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.PublishCorpus
) extends CorpusComponent.CorpusRegistryService.PublishCorpusActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      input <- exec_from(CorpusRevisionCreate.createC(action.record))
      _ <- exec_from(_validate_revision(input))
      revisions <- all_revisions
      existing = revisions.find(x => x.corpusKey == input.corpusKey && x.revision == input.revision)
      response <- existing match {
        case Some(current) if _same_revision(current, input) =>
          exec_pure(OperationResponse(Record.dataAuto(
            "id" -> current.id.value,
            "corpusKey" -> current.corpusKey.value,
            "revision" -> current.revision
          )))
        case Some(_) =>
          exec_from(Consequence.stateConflict(
            s"Corpus revision '${input.corpusKey.value}:${input.revision}' is immutable and already has different content."
          ))
        case None =>
          entity_create(input).map(created => OperationResponse(Record.dataAuto(
            "id" -> created.id.value,
            "corpusKey" -> input.corpusKey.value,
            "revision" -> input.revision
          )))
      }
    } yield response

  private def _validate_revision(input: CorpusRevisionCreate): Consequence[Unit] =
    if (input.corpusKey.value.trim.isEmpty)
      Consequence.operationInvalid("corpusKey must not be empty")
    else if (input.revision <= 0)
      Consequence.operationInvalid("revision must be greater than zero")
    else if (input.sourceDigest.value.trim.isEmpty)
      Consequence.operationInvalid("sourceDigest must not be empty")
    else
      Consequence.unit

  private def _same_revision(current: CorpusRevision, input: CorpusRevisionCreate): Boolean =
    input.name.contains(current.name) &&
      current.kind == input.kind &&
      current.description == input.description &&
      current.sourceDigest == input.sourceDigest
}

private final case class RegisterCorpusCaseActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.RegisterCorpusCase
) extends CorpusComponent.CorpusRegistryService.RegisterCorpusCaseActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      input <- exec_from(CorpusCaseCreate.createC(action.record))
      _ <- exec_from(_validate_case(input))
      parent <- entity_load_option_internal[CorpusRevision](input.corpusRevisionId)
      _ <- parent match {
        case Some(_) => exec_pure(())
        case None => exec_from(Consequence.entityNotFound(
          s"Corpus revision '${input.corpusRevisionId}' does not exist."
        ))
      }
      cases <- all_cases
      existing = cases.find(x => x.corpusRevisionId == input.corpusRevisionId && x.caseKey == input.caseKey)
      response <- existing match {
        case Some(current) if _same_case(current, input) =>
          exec_pure(OperationResponse(Record.dataAuto("id" -> current.id.value, "caseKey" -> current.caseKey.value)))
        case Some(_) =>
          exec_from(Consequence.stateConflict(
            s"Corpus case '${input.caseKey.value}' is immutable and already has different content in revision '${input.corpusRevisionId}'."
          ))
        case None =>
          entity_create(input).map(created => OperationResponse(Record.dataAuto(
            "id" -> created.id.value,
            "caseKey" -> input.caseKey.value
          )))
      }
    } yield response

  private def _validate_case(input: CorpusCaseCreate): Consequence[Unit] =
    if (input.caseKey.value.trim.isEmpty)
      Consequence.operationInvalid("caseKey must not be empty")
    else if (input.applicationPurpose.value.trim.isEmpty)
      Consequence.operationInvalid("applicationPurpose must not be empty")
    else if (input.fixtureReference.value.trim.isEmpty)
      Consequence.operationInvalid("fixtureReference must not be empty")
    else
      Consequence.unit

  private def _same_case(current: CorpusCase, input: CorpusCaseCreate): Boolean =
    current.applicationPurpose == input.applicationPurpose &&
      current.difficulty == input.difficulty &&
      current.fixtureReference == input.fixtureReference &&
      current.expectedEvidenceReference == input.expectedEvidenceReference &&
      current.tags == input.tags
}

private final case class SearchCorpusRevisionsActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.SearchCorpusRevisions
) extends CorpusComponent.CorpusRegistryService.SearchCorpusRevisionsActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      revisions <- all_revisions
      filtered = revisions
        .filter(x => action.record.getString("corpusKey").forall(_ == x.corpusKey.value))
        .filter(x => action.record.getInt("revision").forall(_ == x.revision))
        .filter(x => action.record.getString("text").forall(_matches_text(x, _)))
        .sortBy(x => (x.corpusKey.value, x.revision))
      selected = page(filtered, action.record)
    } yield OperationResponse(Record.dataAuto("items" -> selected.map(x =>
      x.toRecord().upsertSingle("id", x.id.value)
    )))

  private def _matches_text(revision: CorpusRevision, text: String): Boolean = {
    val query = text.trim.toLowerCase(java.util.Locale.ROOT)
    query.isEmpty || Vector(
      revision.corpusKey.value,
      revision.name.value,
      revision.kind.value,
      revision.description.map(_.toI18nString.displayMessage).getOrElse("")
    ).exists(_.toLowerCase(java.util.Locale.ROOT).contains(query))
  }
}

private final case class GetCorpusRevisionActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.GetCorpusRevision
) extends CorpusComponent.CorpusRegistryService.GetCorpusRevisionActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      revisionid <- exec_from(required_entity_id(action.record, "corpusRevisionId"))
      revision <- entity_load_option_internal[CorpusRevision](revisionid)
      value <- exec_from(revision.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Corpus revision '$revisionid' does not exist.")
      ))
    } yield OperationResponse(Record.dataAuto("item" -> value.toRecord()
      .upsertSingle("id", value.id.value)))
}

private final case class GetCorpusCaseActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.GetCorpusCase
) extends CorpusComponent.CorpusRegistryService.GetCorpusCaseActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      caseid <- exec_from(required_entity_id(action.record, "corpusCaseId"))
      corpuscase <- entity_load_option_internal[CorpusCase](caseid)
      value <- exec_from(corpuscase.map(Consequence.success).getOrElse(
        Consequence.entityNotFound(s"Corpus case '$caseid' does not exist.")
      ))
    } yield OperationResponse(Record.dataAuto("item" -> value.toRecord()
      .upsertSingle("id", value.id.value)
      .upsertSingle("corpusRevisionId", value.corpusRevisionId.value)))
}

private final case class ListCorpusCasesActionCallImpl(
  core: ActionCall.Core,
  override val action: CorpusComponent.CorpusRegistryService.ListCorpusCases
) extends CorpusComponent.CorpusRegistryService.ListCorpusCasesActionCall
    with CorpusRegistryActionSupport {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      revisionid <- exec_from(required_entity_id(action.record, "corpusRevisionId"))
      cases <- all_cases
      filtered = cases
        .filter(_.corpusRevisionId == revisionid)
        .filter(x => action.record.getString("applicationPurpose").forall(_ == x.applicationPurpose.value))
        .filter(x => action.record.getString("difficulty").forall(value => x.difficulty.exists(_.value == value)))
        .filter(x => action.record.getString("tag").forall(value => x.tags.exists(_.value == value)))
        .sortBy(_.caseKey.value)
      selected = page(filtered, action.record)
    } yield OperationResponse(Record.dataAuto("items" -> selected.map(x =>
      x.toRecord()
        .upsertSingle("id", x.id.value)
        .upsertSingle("corpusRevisionId", x.corpusRevisionId.value)
    )))
}

object DefaultCorpusRegistryServiceFactory {
  def apply(): DefaultCorpusRegistryServiceFactory = new DefaultCorpusRegistryServiceFactory()
  }

final class DefaultEntityServiceFactory extends CorpusComponent.EntityServiceFactory

object DefaultEntityServiceFactory {
  def apply(): DefaultEntityServiceFactory = new DefaultEntityServiceFactory()
  }

final class AggregateServiceFactoryImpl extends CorpusComponent.AggregateServiceFactory

object AggregateServiceFactoryImpl {
  def apply(): AggregateServiceFactoryImpl = new AggregateServiceFactoryImpl()
}

final class ViewServiceFactoryImpl extends CorpusComponent.ViewServiceFactory

object ViewServiceFactoryImpl {
  def apply(): ViewServiceFactoryImpl = new ViewServiceFactoryImpl()
}
