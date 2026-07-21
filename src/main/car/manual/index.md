# Textus Corpus Reference Manual

Textus Corpus publishes immutable, reviewed corpus revisions and their cases.
Consumers retain the returned revision identity so later corrections cannot
silently change an experiment population.

## Service

`CorpusRegistry` provides these operations:

- `publish-corpus` creates an immutable revision.
- `register-corpus-case` adds a reviewed case to that revision.
- `search-corpus-revisions` and `get-corpus-revision` resolve revisions.
- `get-corpus-case` and `list-corpus-cases` resolve reviewed cases.

Fixture and expected-evidence fields are sanitized resource references. They
must not contain credentials or unrestricted provider payloads.

See [User Guide](user-guide.md) for the normal lifecycle.
