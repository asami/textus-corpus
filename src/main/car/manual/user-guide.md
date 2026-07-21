# Textus Corpus User Guide

1. Publish a corpus revision with a stable key, integer revision, and source
   digest.
2. Register reviewed cases against the returned revision id.
3. Store sanitized fixtures and expected evidence outside the entity record and
   reference them from each case.
4. Select or replay cases by the exact revision id.
5. Publish a new revision for every correction; never replace a reviewed
   revision or case.

Exact retries are idempotent. A retry that changes immutable content is
rejected.
