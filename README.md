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

## Development

- artifact: `textus-corpus`
- package: `org.simplemodeling.textus.corpus`
- version: `0.1.0-SNAPSHOT`

Run `sbt cozyGenerate compile` to regenerate and compile the CAR. Generated
Scala sources are under `target/scala-3.3.8/src_managed/main/scala`.
