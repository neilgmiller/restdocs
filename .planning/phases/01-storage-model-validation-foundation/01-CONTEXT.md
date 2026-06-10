# Phase 1: Storage Model + Validation Foundation - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire `asyncResponse` as a first-class optional field through the full Jackson deserialization →
storage model → domain model → mapper pipeline, and add cross-field validation rules (VALID-01
through VALID-04) including infrastructure for emitting warnings from the `Validatable` lifecycle.

**In scope:**
- `storage.Method`: add `asyncResponse: Response? = null` field
- `domain.Method`: add `asyncResponse: Response? = null` field
- `StorageToDomainMappers`: wire `asyncResponse` using the same `mapToModel(context)` call as `response`
- `Validatable`: extend with a 2-arg overload that accepts a `warningEmitter`
- `ValidationModule` / `ValidatingBeanDeserializerModifier` / `ValidatingDeserializer`: thread `warningEmitter`
- `storage.Method.validate()`: implement VALID-03 (warning) and VALID-04 (error) cross-field checks
- `DocValidator`: wire the `warningEmitter` when creating mappers

**Out of scope (Phase 1):**
- HTML rendering of `asyncResponse` (Phase 3)
- Kotlin code generation of `*AsyncResponse` classes (Phase 2)
- Migrating existing 49 `AsyncCapableResponse` usages (opportunistic)

</domain>

<decisions>
## Implementation Decisions

### Warning Infrastructure (Validatable extension)
- **D-01:** Extend the `Validatable` interface with a 2-arg overload: `fun validate(validationContext: Any?, warningEmitter: (String) -> Unit)`. Provide a default implementation that delegates to the existing 1-arg `validate(validationContext)` — existing implementations require zero changes.
- **D-02:** Thread `warningEmitter: (String) -> Unit` as a **separate constructor parameter** through `ValidationModule` → `ValidatingBeanDeserializerModifier` → `ValidatingDeserializer` → `validate()`. The warning channel is NOT added to `AccumulatingContext` or `FullContext` — keeping context classes free of warning concerns.
- **D-03:** `DocValidator` creates mappers with a `warningEmitter`. Exact surface (stderr vs. `messageHandler` callback) is left to the planner — either routes warnings to callers.

### Cross-field Validation Placement (VALID-03/04)
- **D-04:** `storage.Method` overrides the 2-arg `validate(validationContext, warningEmitter)` and contains both VALID-03 and VALID-04 checks. This is the only `Validatable` that needs the 2-arg override for now.
- **D-05:** VALID-04 (error — `asyncResponse` present but `response` has no `job` field): throw `ValidationException` from `storage.Method.validate()`.
- **D-06:** VALID-03 (warning — `response` has `job` field but `asyncResponse` is absent): call `warningEmitter("WARNING: ...")` from `storage.Method.validate()`.

### "Has Job Field" Detection
- **D-07:** A `response` is treated as "has a job field" if EITHER:
  1. `response.typeRef` is in `{"AsyncJobResponse", "AsyncCapableResponse"}`, OR
  2. `response.fields` contains a field with `name == "job"` (inline object case).
  Any other typeref (non-async named type) is NOT treated as having a job field.

### Type Reuse
- **D-08:** `asyncResponse` uses the existing `storage.Response` and `domain.Response` types verbatim. No new response subclasses. `Response.mapToModel(context)` applied to `asyncResponse` gives VALID-02 (same structural rules) for free.

### Claude's Discretion
- Exact warning message text for VALID-03 and VALID-04
- Whether `warningEmitter` is routed to `System.err` or the existing `messageHandler` in `DocValidator`
- Order of checks within `storage.Method.validate()` (VALID-04 error vs VALID-03 warning)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spec and Requirements
- `.claude/docbuild-async-response-spec.md` — Format change spec: three cases (pure async, mixed, sync), validator rules, migration notes
- `.planning/REQUIREMENTS.md` — FOUND-01/02/03, VALID-01/02/03/04 with acceptance criteria

### Storage Model
- `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt` — Add `asyncResponse: Response? = null` field; override `validate(context, warningEmitter)`
- `src/main/java/com/giffardtechnologies/restdocs/storage/Response.kt` — Reused type for `asyncResponse`; note `typeRef: String?` and `fields: ArrayList<FieldListElement>?` for D-07

### Domain Model
- `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` — Add `asyncResponse: Response? = null` field
- `src/main/java/com/giffardtechnologies/restdocs/domain/Response.kt` — Reused domain response type

### Mapper
- `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` — `MethodStorageModel.mapToModel()` at line 506; `ResponseStorageModel.mapToModel()` at line 534 (reused verbatim for asyncResponse)

### Validation Infrastructure
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt` — Interface to extend with 2-arg overload
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt` — Add `warningEmitter` constructor param
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt` — Thread `warningEmitter`
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt` — Thread `warningEmitter`; call 2-arg `validate()`
- `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt` — Wire `warningEmitter` into `createMapper()` calls

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `storage.Response` / `domain.Response`: Reused verbatim for `asyncResponse` — no new types needed
- `ResponseStorageModel.mapToModel(context)` (line 534): Applied to `asyncResponse` exactly as for `response`
- `ValidationModule(validationContext)`: Constructor pattern to extend with `warningEmitter`

### Established Patterns
- `Validatable.validate(validationContext: Any?)`: Override in each storage model needing validation; throws `ValidationException` for errors. New 2-arg overload follows the same pattern.
- `@JsonProperty("typeref")` annotation: Used on `Response.typeRef` — no Jackson annotation needed on `Method.asyncResponse` (key matches field name)
- `createMapper(validationContext)`: Called twice in `DocValidator.getValidatedDocument()` — both calls need `warningEmitter`
- `FAIL_ON_UNKNOWN_PROPERTIES` default true: The `storage.Method` field addition is a hard prerequisite — YAML files with `asyncResponse` will throw `UnrecognizedPropertyException` until the field exists

### Integration Points
- `storage.Method.validate()` already has `if (validationContext is DocValidator.AccumulatingContext)` / `else` branching — VALID-03/04 checks go in the `else` branch (FullContext pass, where type names are available for D-07 typeref check)
- `ValidatingDeserializer.deserialize()` at line 67-73: The single call site that invokes `validate()` — add the 2-arg call here

</code_context>

<specifics>
## Specific Ideas

- The 2-arg `Validatable` overload should default-delegate to the 1-arg form so existing implementations compile without changes
- VALID-04 check (error) fires when `this.asyncResponse != null && !responseHasJobField(this.response)` — only if asyncResponse is actually present
- VALID-03 check (warning) fires when `responseHasJobField(this.response) && this.asyncResponse == null` — advisory, migration-friendly
- D-07 helper `responseHasJobField(response: Response?): Boolean` can be a private function in `storage/Method.kt` or the validation package

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 1-Storage Model + Validation Foundation*
*Context gathered: 2026-06-02*
