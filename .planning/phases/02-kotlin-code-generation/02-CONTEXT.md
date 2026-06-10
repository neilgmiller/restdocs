# Phase 2: Kotlin Code Generation - Context

**Gathered:** 2026-06-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend `MethodProcessor` to emit a typed `*AsyncResponse` data class alongside the existing `*Request`/`*Response` classes for any method whose `domain.Method.asyncResponse` field is non-null. Also extend `MethodClassNames` to expose `asyncResponseClassName: ClassName?` for external consumers.

**In scope:**
- `MethodProcessor.processMethod()`: detect `method.asyncResponse`, generate `*AsyncResponse` TypeSpec when `asyncResponse.typeSpec` is `ObjectSpec`, write it into the same file as the request class
- `MethodClassNames` data class: add `asyncResponseClassName: ClassName?` (null when no asyncResponse)
- `MethodProcessor.getClassNames()`: populate `asyncResponseClassName` using the same naming logic as `responseClassName`
- Unit test: construct `domain.Method` with inline `asyncResponse` directly, assert generated TypeSpec
- Smoke test: minimal synthetic YAML in `src/test/resources` run through full `KotlinGenerator.generate()`, assert output file

**Out of scope (Phase 2):**
- HTML rendering of `asyncResponse` (Phase 3)
- Migrating existing 49 `AsyncCapableResponse` usages to add `asyncResponse` blocks
- Adding `AsyncJobResponse` named type to the real API doc YAML
- `SwiftAPIServerClient` changes to use `asyncResponseClassName`

</domain>

<decisions>
## Implementation Decisions

### TypeRef asyncResponse handling
- **D-01:** When `asyncResponse.typeSpec` is NOT an `ObjectSpec` (e.g., it is a `TypeRefSpec`), codegen silently produces nothing — no `*AsyncResponse` class, no log. This mirrors the existing `response` TypeRefSpec behavior in `createResponseClassDefinition()`. In practice this case will never occur: the real API spec has no `asyncResponse` entries yet, and new ones are always inline objects with fields.

### MethodClassNames extension
- **D-02:** `MethodClassNames` is extended with a third field: `asyncResponseClassName: ClassName?`. It is `null` (not `Unit::class.asClassName()`) when the method has no `asyncResponse` block — semantically `null` is the correct signal for "this class does not exist."
- **D-03:** `getClassNames()` populates `asyncResponseClassName` using the same `ClassName(requestsPackage, methodName + "AsyncResponse")` pattern as `responseClassName` uses for `ObjectSpec`, conditioned on `method.asyncResponse != null`.

### Response class co-location
- **D-04 (Claude's discretion):** The `*AsyncResponse` TypeSpec is written into the same file as the `*Request` and `*Response` classes (if any), following the existing pattern for `createResponseClassDefinition()`. No separate file is created for async response.

### Test approach
- **D-05:** Two test artifacts:
  1. **Unit test** — build `domain.Method` in-memory with an `asyncResponse: Response` containing an `ObjectSpec` with sample fields. Call `processMethod()` and assert the output file contains a `*AsyncResponse` data class with the expected properties.
  2. **Smoke test** — a minimal synthetic YAML fixture in `src/test/resources` covering all three spec cases:
     - Case 1: `response: { typeref: AsyncJobResponse }` + `asyncResponse: { type: object, fields: [...] }` (pure async)
     - Case 2: `response: { type: object, fields: [..., job] }` + `asyncResponse: { type: object, fields: [...] }` (mixed)
     - Case 3: `response: { type: object, fields: [...] }` with no `asyncResponse` (regression)
     Run `KotlinGenerator.generate()` end-to-end and assert Case 1 and 2 produce `*AsyncResponse` files; Case 3 produces no `*AsyncResponse` file.

### Claude's Discretion
- Exact class name for `asyncResponseClassName` when `asyncResponse.typeSpec` is a TypeRefSpec — null or the referenced ClassName. Given D-01, null is correct for the skip case.
- Whether to add `createAsyncResponseClassDefinition()` as a parallel private helper or handle the logic inline in `processMethod()` — planner chooses.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spec and Requirements
- `.planning/REQUIREMENTS.md` — CODEGEN-01 with acceptance criteria (the single requirement for this phase)
- `.planning/ROADMAP.md` — Phase 2 success criteria (3 items)

### Phase 1 Context (decisions that carry forward)
- `.planning/phases/01-storage-model-validation-foundation/01-CONTEXT.md` — D-08: asyncResponse reuses `domain.Response` verbatim; no new response subclasses

### Domain Model
- `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` — `asyncResponse: Response?` field (added Phase 1); the source field that drives generation
- `src/main/java/com/giffardtechnologies/restdocs/domain/Response.kt` — `typeSpec` field (determines ObjectSpec vs TypeRefSpec)

### Code Generation Layer
- `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt` — primary change target; `processMethod()`, `getClassNames()`, `MethodClassNames`, `createResponseClassDefinition()` (pattern to mirror for async)
- `src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt` — orchestrates MethodProcessor; uses `getClassNames()` in `SwiftAPIServerClient` generation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `createResponseClassDefinition(response: Response?, responseClassName: ClassName): TypeSpec?` (MethodProcessor.kt:393) — mirror this exact function for `asyncResponse`; the pattern is `when (typeSpec) { is ObjectSpec → processObjectToTypeSpec(...); else → null }`
- `objectProcessor.processObjectToTypeSpec(className, typeSpec, useFutureProofEnum = true, ...)` — same call used for `*Response`; reuse verbatim for `*AsyncResponse`
- `StringUtils.capitalize(method.name)` — gives `methodName`; async class name is `methodName + "AsyncResponse"`

### Established Patterns
- `MethodClassNames(requestClassName, responseClassName)` at MethodProcessor.kt:169 — extend to 3-field data class; callers that destructure with `val (req, res) = getClassNames(method)` will break and need updating
- Response class in same file as request: `file(requestClassName) { addType(request); responseClassTypeSpec?.let { addType(it) } }.writeTo(codeDirectory)` — add `asyncResponseClassTypeSpec?.let { addType(it) }` alongside
- `responseClassName` for TypeRefSpec: `ClassName(typeRefPackage, response.typeSpec.referenceName)` — for asyncResponse TypeRefSpec (D-01: skip), asyncResponseClassName = null

### Integration Points
- `KotlinGenerator.kt` line ~92: `methodProcessor.processMethod(it)` — no change needed; MethodProcessor handles async internally
- `KotlinGenerator.kt` `SwiftAPIServerClient` section: calls `methodProcessor.getClassNames(it)` and destructures — if `MethodClassNames` gains a 3rd field, this destructuring must be updated (or use named properties)
- `MethodProcessor.writeSupportingFiles()` — no change needed for Phase 2

</code_context>

<specifics>
## Specific Ideas

- The `MethodClassNames` destructuring in `SwiftAPIServerClient` (`val (requestClassName, responseClassName) = methodProcessor.getClassNames(it)`) will need updating — either add `_` for the third field or switch to named property access.
- Synthetic test YAML must include a minimal valid service/method structure matching what `DocValidator.getValidatedDocument()` expects — look at the existing storage model for required fields.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 2-kotlin-code-generation*
*Context gathered: 2026-06-05*
