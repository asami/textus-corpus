# Development Script Prepare Separation Hygiene

Status: Open hygiene

## Finding

The development server has partial separation through
`scripts/update-runtime-classpath.sh` and an SBT-free `scripts/run-server.sh`.
However, the evidence is presence-only, `scripts/run-server-debug.sh` still
launches top-level SBT, and there is no aggregate prepare entry or freshness
manifest comparable to ArtScene.

## Required direction

- Introduce one serialized prepare entry for build, CAR, dependency, and
  runtime-evidence work.
- Make all maintained runtime entry points consume the prepared result without
  top-level SBT.
- Use explicit input/artifact digests to reject missing or stale evidence.
- Preserve compile-and-restart without unrelated re-preparation.

## Completion evidence

Prepare succeeds through the shared serialized runner; runtime entry points are
statically SBT-free; current evidence is accepted; missing or stale evidence
fails actionably without implicit preparation.
