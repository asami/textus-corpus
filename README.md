# Textus Corpus

`textus-corpus` owns reviewed, versioned evaluation inputs. A `CorpusRevision`
contains stable metadata and a `CorpusCase` refers to sanitized input fixtures
and expected-evidence resources. Corrections publish a new revision; callers
must identify the revision they consumed.

It deliberately does not own:

- AI provider, model, endpoint, credentials, or runtime profile selection;
- experiment arms, execution runs, observations, or aggregated metrics;
- application-specific acceptance logic.

Those responsibilities belong respectively to Textus AI, `textus-experiment`,
and the application which defines the acceptance operation.

## Contract

The `CorpusRegistry` surface exposes the semantic operations:

- `publish-corpus`
- `register-corpus-case`
- `get-corpus-revision`
- `list-corpus-cases`

The implementation exposes only this semantic registry service. Publishing the
same key/revision or case key with identical content is idempotent; replacement
content is rejected. Generic aggregate, view, and entity mutation services are
not part of the component protocol.

## Operation Evaluation Development Adapter

`OfflineCorpusEvaluationSinkAdapter` implements CNCF's standard
`CorpusEvaluationSink` for bounded offline and development verification. It
retains automatic operation start/terminal facts and application-submitted
corpus candidates in delivery order, deduplicates exact fact-ID retries, rejects
replacement content for an existing fact ID, and reports capacity saturation
through the standard delivery limitation.

The primary component exposes this adapter through the standard SPI provider
contract. Its provider accepts only an absent or explicit `offline` mode. The
adapter is deliberately in-memory and non-persistent; persistent candidate
registration, review/promotion workflow, retention policy, and production
provider activation remain separately owned follow-up work. The adapter does
not promote a candidate into a `CorpusRevision` or `CorpusCase`.

## Development

- artifact: `textus-corpus`
- package: `org.simplemodeling.textus.corpus`
- version: `0.1.0-SNAPSHOT`

Run `sbt cozyGenerate compile` to regenerate and compile the CAR. Generated
Scala sources are under `target/scala-3.3.8/src_managed/main/scala`.
