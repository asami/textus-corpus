# 2026-10-06 AI Evaluation Case Corpus

Textus AI Evaluation introduces replayable EvaluationCases derived from actual AI executions.

textus-corpus is responsible for corpus organization and reusable EvaluationCase indexing. It should not become the authoritative store for duplicated prompt/output payloads merely for benchmarking. Large input/output/context data remains owned by AI Runtime audit or the relevant source; EvaluationCase keeps references plus classification and evaluation metadata.

Expected EvaluationCase information includes source execution, task class, context/input/original-output references, later outcome reference, and evaluation policy.

The same case must be reusable across multiple EvaluationRuns and future Thinking Engines. This allows a new local or provider model to be evaluated against historical real-world Textus tasks without having planned an A/B test when those tasks originally ran.

The evaluation orchestration, Arm management, scoring, and aggregation remain responsibilities of textus-experiment.

No implementation phase is created yet. The cross-component contract should be stabilized first.


## Environment restoration decision

A corpus case must also be executable, so environment restoration is assigned to textus-corpus.

The initial eligibility rule is deliberately strict: one Git repository, immediately after a commit, with a clean working tree. The EvaluationCase records the repository and exact commit. textus-corpus restores that state as a `GitCommitEnvironment` / `RestoredEnvironment`.

For this first level, source files are not copied into the corpus. Git history is the authoritative source and the corpus records the information needed to reconstruct it.

textus-experiment consumes the restored environment and must not implement its own Git checkout/snapshot mechanism.

Working-tree diffs, multi-repository workspaces, and external resources are deferred until concrete cases require them.
