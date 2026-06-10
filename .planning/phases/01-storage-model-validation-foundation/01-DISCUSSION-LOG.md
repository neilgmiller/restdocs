# Phase 1: Storage Model + Validation Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-02
**Phase:** 1-Storage Model + Validation Foundation
**Areas discussed:** Cross-field validation placement, VALID-03 warning output mechanism, "Has job" detection for typeref responses

---

## Cross-field validation placement

| Option | Description | Selected |
|--------|-------------|----------|
| In MethodStorageModel.mapToModel() | Consistent with "validation at mapping time" pattern; both fields available on storage Method | |
| In storage.Method.validate() | Jackson deserialization lifecycle; can throw for errors | ✓ (after clarification) |
| Post-mapping pass in DocValidator | Separate pass after domain mapping; breaks validation-at-mapping-time convention | |

**User's choice:** Initially this was the first question, but the user correctly pointed out that DocValidator does NOT run mappings — it returns storage.Document. This ruled out the mapper option entirely. Discussion evolved to compare `storage.Method.validate()` vs. a post-deserialization loop in DocValidator.

**Notes:** User identified that the ARCHITECTURE.md codebase map was misleading — it described validation as happening during mapping, but DocValidator only does two Jackson deserialization passes and returns storage.Document. KotlinGenerator calls `.mapToModel()` separately. This changes the option set significantly.

---

## VALID-03 Warning Output Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| On both AccumulatingContext and FullContext | Warnings accumulate on context objects; DocValidator collects after each pass | |
| FullContext only | Simpler — one collection point, warnings only in second pass | |
| Shared WarningCollector interface on ValidationContext | Most general; adds third type to hierarchy | |
| Pass warningEmitter into ValidationModule directly | Thread (String) -> Unit alongside validationContext; context classes stay clean | ✓ |

**User's choice:** "Pass a warning emitter into ValidationModule, and pass it down. That's better than requiring all contexts to handle warnings."

**Notes:** User proposed extending the Validatable/ValidationModule infrastructure with a separate `warningEmitter: (String) -> Unit` parameter rather than adding warnings to the context class hierarchy. User confirmed they can foresee other warnings in the future — making this reusable infrastructure the right investment. Combined with the cross-field placement decision: `storage.Method.validate()` overrides the new 2-arg `validate(context, warningEmitter)` to handle VALID-03/04.

---

## "Has Job" Detection for Typeref Responses

| Option | Description | Selected |
|--------|-------------|----------|
| Check typeref name + inline fields | If response.typeRef in {"AsyncJobResponse", "AsyncCapableResponse"}, treat as having job; else scan inline fields | ✓ |
| Resolve the typeref and inspect its fields | Walk document context to find the named type and its fields; correct for any typeref | |
| Skip the check when response is a typeref | Skip VALID-03/04 entirely when typeRef is non-null; silently misses invalid combos | |

**User's choice:** "Check typeref name + inline fields" with the known set `{"AsyncJobResponse", "AsyncCapableResponse"}` only — not pattern-based matching.

**Notes:** Case 1 from the spec (`response: typeref: AsyncJobResponse`) has no inline fields, so inline-only detection would falsely trigger VALID-04. The known-set approach is explicit and stable. Future async typerefs would require a deliberate code change to add to the set.

---

## Claude's Discretion

- Exact warning message text for VALID-03 and VALID-04
- Whether `warningEmitter` routes to `System.err` or the existing `messageHandler` callback in `DocValidator`
- Order of VALID-03 vs VALID-04 checks within `storage.Method.validate()`

## Deferred Ideas

None — discussion stayed within Phase 1 scope.
