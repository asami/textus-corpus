# Textus Corpus Environment Restoration

## Purpose

A replayable corpus must be able to reconstruct the execution environment required by an EvaluationCase. textus-corpus therefore owns environment restoration in addition to corpus organization and EvaluationCase indexing.

The first implementation target is intentionally narrow: restore a single Git repository at an exact clean commit.

## Initial eligibility

An AI execution can be curated into the initial replay corpus when:

- its source context is one Git repository,
- the relevant source state is immediately after a commit,
- the working tree is clean,
- repository identity and exact commit are known,
- the AI execution/audit references required by the EvaluationCase are available.

This avoids introducing workspace snapshots, copied source trees, patch capture, or multi-repository reconstruction before they are actually needed.

## GitCommitEnvironment

The initial environment representation is conceptually:

```text
GitCommitEnvironment
  repository
  commit
```

An EvaluationCase refers to this environment specification.

Restoration is deterministic:

```text
EvaluationCase
      |
      v
textus-corpus.restore
      |
      +-- obtain repository
      +-- checkout exact commit
      +-- verify clean restored workspace
      |
      v
RestoredEnvironment
```

The repository itself remains the authoritative source history. The corpus does not copy the complete source tree merely to preserve the case.

## Component boundary

- textus-corpus owns environment specification and restoration.
- textus-experiment requests/receives a RestoredEnvironment and executes experiment Arms.
- textus-experiment does not own Git checkout or source snapshot logic.
- textus-ai-core / AI Runtime provides execution/audit references.
- sm-workflow provides task and outcome correlation used when curating EvaluationCases.

## Future extensions

Possible later environment types include a single repository plus working-tree changes, multiple repositories, and external resources. These are explicitly outside the initial scope.

Add them only after actual corpus candidates require them. The initial goal is to prove the replay/evaluation model with the simplest reproducible environment.
