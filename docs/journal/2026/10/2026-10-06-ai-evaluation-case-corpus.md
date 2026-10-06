# 2026-10-06 AI Evaluation Case Corpus

Textus AI Evaluation introduces replayable EvaluationCases derived from actual AI executions.

textus-corpus is responsible for corpus organization and reusable EvaluationCase indexing. It should not become the authoritative store for duplicated prompt/output payloads merely for benchmarking. Large input/output/context data remains owned by AI Runtime audit or the relevant source; EvaluationCase keeps references plus classification and evaluation metadata.

Expected EvaluationCase information includes source execution, task class, context/input/original-output references, later outcome reference, and evaluation policy.

The same case must be reusable across multiple EvaluationRuns and future Thinking Engines. This allows a new local or provider model to be evaluated against historical real-world Textus tasks without having planned an A/B test when those tasks originally ran.

The evaluation orchestration, Arm management, scoring, and aggregation remain responsibilities of textus-experiment.

No implementation phase is created yet. The cross-component contract should be stabilized first.
