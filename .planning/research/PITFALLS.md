# Domain Pitfalls: asyncResponse Block

**Domain:** Kotlin toolchain extension (Jackson/MapStruct/KotlinPoet/Velocity)
**Researched:** 2026-06-02
**Confidence:** HIGH (codebase evidence, not web research)

## Critical Pitfalls

### Pitfall 1: Jackson 3.x Fails on Unknown YAML Keys by Default

**What goes wrong:** Adding `asyncResponse` to YAML spec files without first updating the storage `Method` class causes immediate `UnrecognizedPropertyException` during deserialization. Jackson 3.x (used here via `tools.jackson:jackson-databind:3.1.0`) fails on unknown properties by default, and the mapper configuration in `JacksonMapperBuilder.kt` does NOT disable `FAIL_ON_UNKNOWN_PROPERTIES`. There is no `@JsonIgnoreProperties(ignoreUnknown = true)` on the `Method` class either.

**Why it happens:** The toolchain performs two-pass deserialization (see `DocValidator.getValidatedDocument()`). Both passes will fail if the YAML contains a key that the storage model doesn't declare. This is not a "soft" failure — it throws before validation even begins.

**Consequences:** Any YAML file with an `asyncResponse` key will fail to parse entirely, blocking all three CLI tools (doc_generator, kotlin_generator, doc_validator). Existing specs are safe only if the key is not present, but you cannot test `asyncResponse` at all until this is fixed.

**Warning signs:**
- `UnrecognizedPropertyException` mentioning "asyncResponse" when running any CLI tool
- Test YAML files that include `asyncResponse` cannot be loaded

**Prevention:**
- Add `val asyncResponse: Response? = null` to `storage.Method` as the FIRST implementation step, before writing any YAML test fixtures
- This is a strict dependency — nothing else in the pipeline can be tested without this

**Detection:** Run `doc_validator` on a YAML fixture containing `asyncResponse`. If it crashes rather than validating, the field is missing from the storage model.

**Relevant phase:** Phase 1 (Storage model). Must be done first.

---

### Pitfall 2: Two-Model Divergence — HTML Gen Uses Storage Model, Kotlin Gen Uses Domain Model

**What goes wrong:** The `DocGenerator` passes the **storage** `Document` directly to Velocity templates (see `DocGenerator.generateHTML()` line 30: it calls `DocValidator().getValidatedDocument()` which returns `storage.Document`). The `ObjectInspectionHelper` works entirely with `com.giffardtechnologies.restdocs.storage.*` types. In contrast, `KotlinGenerator` maps to domain first via `mapToModel()`.

If you add `asyncResponse` only to the domain model (following the storage-to-domain-to-codegen pipeline assumption), the Velocity templates will never see it. Conversely, if you forget to map it in `StorageToDomainMappers.kt`, the Kotlin generator will silently produce no async response class.

**Why it happens:** The architecture documentation describes a clean layered pipeline, but the HTML generation path shortcuts it — it uses the raw storage model without mapping to domain. This means `asyncResponse` must exist and be accessible on BOTH `storage.Method` AND `domain.Method`, and the template helpers in `ObjectInspectionHelper` must gain async-awareness.

**Consequences:**
- asyncResponse renders in HTML but not in Kotlin code (forgot domain mapping)
- asyncResponse generates Kotlin classes but renders nothing in HTML (forgot template/helper update)
- Bugs where HTML shows raw storage structure while Kotlin shows processed domain structure

**Warning signs:**
- The field appears in one output but not the other
- `ObjectInspectionHelper` has no method that accepts or inspects `asyncResponse`

**Prevention:**
- Treat the storage model, domain model, mapper, template helper, AND template as a single atomic change set
- Add a `hasAsyncResponse(method: Method)` helper to `ObjectInspectionHelper` referencing `storage.Method`
- The mapper in `StorageToDomainMappers.kt` already maps `response` at line 517 (`response = response?.mapToModel(context)`) — add `asyncResponse` mapping immediately below using the same `mapToModel` function

**Detection:** Write a single integration test YAML fixture with `asyncResponse` and verify both HTML output (contains "Async Response" section) and Kotlin output (contains `*AsyncResponse` class).

**Relevant phase:** Phase 2 (Mapping) and Phase 4 (Rendering). The divergent paths mean these must be tested together.

---

### Pitfall 3: TypeRefSpec Resolution Requires `AsyncJobResponse` in the Type Registry

**What goes wrong:** When `asyncResponse` uses `typeref: SomeType` (or when `response` uses `typeref: AsyncJobResponse`), the `TypeSpec.TypeRefSpec` constructor calls `context.getTypeByName(referenceName)` at domain mapping time (see `StorageToDomainMappers.kt` line 347). If `AsyncJobResponse` is not registered as a data object in the YAML document's `data objects` section or in `service.common.response objects`, the `check()` in `Document.getTypeByName()` will throw: "Type reference to undefined type: AsyncJobResponse."

**Why it happens:** The validation occurs in the `FullContext` pass of `DocValidator`. The set of `referencableTypes` is built from `dataObjects + enumerations + bitsets + responseDataObjects` (line 50-51 of DocValidator). A new typeref name must be present in one of these collections.

**Consequences:** YAML files using `typeref: AsyncJobResponse` will fail with a cryptic error about undefined types. This will look like a validation bug rather than a missing data definition.

**Warning signs:**
- "Type reference to undefined type: AsyncJobResponse" exception
- Works fine when `asyncResponse` uses inline `type: object` with `fields:`, but fails with `typeref:`

**Prevention:**
- Define `AsyncJobResponse` in the YAML document's `data objects` or `response objects` section BEFORE any method references it
- Alternatively, ensure any test fixture that uses `typeref: AsyncJobResponse` includes the type definition
- The validator's `AccumulatingContext` (first pass) must also accumulate the new type name

**Detection:** Create a test fixture with `response: { typeref: AsyncJobResponse }` and verify the validator passes (not just that `asyncResponse` works with inline types).

**Relevant phase:** Phase 1 (Storage model + YAML fixture setup). Must define the type before referencing it.

---

### Pitfall 4: MethodProcessor Class Name Collision with `*AsyncResponse`

**What goes wrong:** The `MethodProcessor.getClassNames()` method (line 177-197) constructs response class names as `"${methodName}Response"`. If you naively add async response class generation as `"${methodName}AsyncResponse"`, you create a naming pattern that could collide with methods whose names end in "Async" (e.g., a method named `processAsync` would generate `ProcessAsyncResponse` for the sync response and `ProcessAsyncAsyncResponse` for the async variant).

More subtly, the `createResponseClassDefinition()` method (line 393-410) only handles `ObjectSpec` and `TypeRefSpec`. If `asyncResponse` uses an `ArraySpec` or other type, it will return `null` and no class is generated — silently dropping the async response.

**Why it happens:** The existing `getClassNames()` only considers `response.typeSpec` patterns of `TypeRefSpec` (resolves to existing class) or `ObjectSpec` (generates new class). Other TypeSpec variants (ArraySpec, BasicSpec, etc.) fall to `else -> null`, meaning the response class becomes `Unit`. The same logic must be carefully extended for `asyncResponse`.

**Consequences:**
- Generated async response class names are awkward or colliding
- Async responses that are arrays or primitives silently generate no class (or default to `Unit`)
- The `SwiftAPIServerClient` generation in `KotlinGenerator` (line 126) also calls `getClassNames()` — it will miss async response types entirely

**Warning signs:**
- Methods with certain naming patterns produce duplicate class files
- YAML entries with `asyncResponse: { type: array, items: ... }` produce no generated class
- `SwiftAPIServerClient` has no awareness of async response types

**Prevention:**
- Use a distinct naming pattern: `"${methodName}AsyncResponse"` and verify no existing method names would collide in the actual API spec
- Handle ALL TypeSpec variants in the async response class generation, not just ObjectSpec
- Decide upfront whether array/primitive async responses need a wrapper class or should use existing types
- Update `MethodClassNames` data class to include an optional `asyncResponseClassName`

**Detection:** Search the YAML spec for methods with "Async" in their name. Test with an `asyncResponse` that is `type: array` to verify the codegen handles it.

**Relevant phase:** Phase 3 (Kotlin code generation).

---

## Moderate Pitfalls

### Pitfall 5: The `response?.mapToModel("response", context)` Identifier String

**What goes wrong:** In `StorageToDomainMappers.kt` line 534-538, the `ResponseStorageModel.mapToModel()` function passes the hardcoded string `"response"` as the `typeSpecIdentifier` for error messages. If you copy this pattern for `asyncResponse`, error messages will say "response must have one of [type, typeref]" even when the actual problem is in `asyncResponse`. Debugging which response block has the error becomes difficult.

**Prevention:**
- Pass `"asyncResponse"` (or `"asyncResponse of '${method.name}'"`) as the identifier when mapping the async response
- Better: pass the method name into both calls for full context, e.g., `"response of '${name}'"` and `"asyncResponse of '${name}'"` 

**Detection:** Trigger a validation error on an `asyncResponse` block and check whether the error message correctly identifies which block is invalid.

**Relevant phase:** Phase 2 (Mapping).

---

### Pitfall 6: Velocity Template Null Safety — `$method.asyncResponse` When Absent

**What goes wrong:** The Velocity template (specifically the `docs/rest_api_doc.vm` which renders the storage model) currently does not render methods (it only handles data objects and enumerations). The root-level `rest_api_doc.vm` DOES render methods. If you add `asyncResponse` rendering to either template without null-guarding, Velocity will render the literal string `"$method.asyncResponse"` for methods where `asyncResponse` is null (Velocity's default behavior for null references is to output the reference text).

**Why it happens:** Velocity does not throw on null dereferencing by default. It prints the variable reference as a string. This is silent corruption of the HTML output — no error, just wrong content.

**Prevention:**
- Always use `#if($method.asyncResponse)` guard before accessing `$method.asyncResponse` properties
- Use the "quiet" reference syntax `$!{method.asyncResponse}` if you want empty string on null
- Add a `hasAsyncResponse(method)` method to `ObjectInspectionHelper` and use it as the template guard

**Detection:** Render a method without `asyncResponse` and inspect the HTML for literal `$method.asyncResponse` strings.

**Relevant phase:** Phase 4 (HTML rendering).

---

### Pitfall 7: Domain `Method` Class Uses `var` for `response` — Mutable State Risk

**What goes wrong:** The domain `Method` class declares `var response: Response? = null` (line 19 of domain/Method.kt). If you add `var asyncResponse: Response? = null` following the same pattern, other code could mutate it after construction. More importantly, `response` and `asyncResponse` share the same type (`Response`), which means any code that processes `response` could accidentally be passed `asyncResponse` with no type-safety distinction.

**Why it happens:** Historical design — the domain model uses mutable properties despite the architecture's stated preference for immutability.

**Prevention:**
- Add `asyncResponse` as `val` (not `var`) in the constructor if possible; if existing patterns force `var`, document why
- Do NOT create a shared processing function that takes a `Response` without also taking a label/context parameter to distinguish which response is being processed
- In `MethodProcessor`, keep the async response processing in a clearly separate code path from synchronous response processing

**Detection:** Search for reassignment of `method.asyncResponse` after construction. If found, it indicates a mutation bug risk.

**Relevant phase:** Phase 2 (Domain model addition).

---

### Pitfall 8: Validation Cross-Constraint — `asyncResponse` Without `job` Field Detection

**What goes wrong:** The spec requires validation: "asyncResponse without a job in response should be flagged as an error." Detecting whether `response` contains a `job` field is non-trivial because:
1. `response` may use `typeref: AsyncJobResponse` (must resolve the typeref to check fields)
2. `response` may have inline fields where `job` is present directly
3. `response` may use `include` elements that pull in `job` from another data object
4. The field might be named `job` in `name` but `asyncJobId` in `longName`

Checking only the simple case (inline `fields` with `name == "job"`) will miss typeref-based patterns, producing false-positive errors on valid specs.

**Prevention:**
- For the initial implementation, check at the STORAGE model level (simpler): if `response.typeRef` is `"AsyncCapableResponse"` or `"AsyncJobResponse"`, treat it as having `job`
- For inline fields, scan `response.fields` for any field with `name == "job"` OR `longName` containing "asyncJobId"
- Document this as a known limitation: include-based job fields are not detected in v1
- Make the "asyncResponse without job" check a WARNING first, promote to error after validation proves reliable

**Detection:** Write test cases for all three response patterns (typeref, inline, include-based) and verify the validator correctly identifies presence/absence of `job`.

**Relevant phase:** Phase 2 (Validation logic).

---

## Minor Pitfalls

### Pitfall 9: `docs/rest_api_doc.vm` vs Root `rest_api_doc.vm` — Which Template Gets Updated?

**What goes wrong:** There are TWO Velocity templates in the project: `/docs/rest_api_doc.vm` (255 lines, renders data objects/enums only, uses `ObjectInspectionHelper`) and `/rest_api_doc.vm` (180 lines, renders methods/resources, older format). It is unclear which is the "active" template. Updating the wrong one means the feature never appears in production output.

**Prevention:**
- Check `docs/docbuild.properties` to determine which template is referenced
- The `docs/` template is clearly newer (uses helper objects, has nav sidebar), but it currently does NOT render methods — so adding async response rendering there has no effect until method rendering is also added
- If method rendering must be added to the `docs/` template as part of this milestone, that is a much larger task than the spec implies

**Detection:** Run `doc_generator` with the production properties file and check which template file is loaded (visible in verbose output).

**Relevant phase:** Phase 4 (HTML rendering). Scope question — may need to be raised to project owner.

---

### Pitfall 10: No Test Coverage for the Mapper — Regressions Are Silent

**What goes wrong:** `StorageToDomainMappers.kt` has ZERO test coverage (documented in CONCERNS.md). Any bug in the `asyncResponse` mapping (e.g., passing wrong context, wrong identifier string, null handling) will only surface when running the full CLI pipeline. There are no unit tests to catch mapper regressions.

**Prevention:**
- Write at least one mapper test as part of this milestone that verifies: `storage.Method` with `asyncResponse` maps to `domain.Method` with non-null `asyncResponse`
- Write a second test verifying: `storage.Method` without `asyncResponse` maps to `domain.Method` with null `asyncResponse`
- These tests also serve as regression guards for the 49 existing methods that lack `asyncResponse`

**Detection:** Mapper bugs manifest as NPE in downstream processors or silent null values where data was expected.

**Relevant phase:** Phase 2 (Mapping). Tests should be written alongside the mapping code.

---

### Pitfall 11: `SwiftAPIServerClient` Generation Assumes Single Response Type

**What goes wrong:** `KotlinGenerator.kt` (lines 125-182) generates `execute()`, `executeBlocking()`, and `executeForResult()` methods using `responseClassName` from `methodProcessor.getClassNames()`. This only handles the synchronous response type. If a method has an `asyncResponse`, the generated client has no way to deserialize or return the async payload type.

**Prevention:**
- For this milestone (per the Out of Scope section), the `SwiftAPIServerClient` does NOT need to handle async response types — but it should not BREAK either
- Ensure `getClassNames()` returns `AsyncJobResponse` (or the synchronous response class) as the `responseClassName` for async methods — this maintains backward compatibility
- Document that async response deserialization in the generated client is out of scope

**Detection:** Verify that methods using `typeref: AsyncJobResponse` still generate valid `execute()` return types in the Swift client.

**Relevant phase:** Phase 3 (Code generation). Low risk if scope is respected.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Storage model (Phase 1) | Jackson fails on unknown key (Pitfall 1) | Add field to storage.Method FIRST before any YAML fixtures |
| Storage model (Phase 1) | TypeRef registry (Pitfall 3) | Define AsyncJobResponse in data objects before referencing it |
| Mapping (Phase 2) | Two-model divergence (Pitfall 2) | Update storage, domain, AND mapper together |
| Mapping (Phase 2) | Error message ambiguity (Pitfall 5) | Pass distinct identifier strings for asyncResponse |
| Mapping (Phase 2) | Validation cross-constraint complexity (Pitfall 8) | Start with simple heuristic, make it a warning not error |
| Mapping (Phase 2) | No test coverage (Pitfall 10) | Write mapper tests alongside the mapping code |
| Code generation (Phase 3) | Class name collision (Pitfall 4) | Validate naming against actual spec method names |
| Code generation (Phase 3) | Non-object TypeSpec handling (Pitfall 4) | Handle ArraySpec/BasicSpec explicitly, not just ObjectSpec |
| HTML rendering (Phase 4) | Template null safety (Pitfall 6) | Use `#if` guards and helper methods |
| HTML rendering (Phase 4) | Wrong template updated (Pitfall 9) | Verify which template is production-active |
| HTML rendering (Phase 4) | Velocity uses storage model (Pitfall 2) | Add helper methods to ObjectInspectionHelper |

## Implementation Order Dependency Chain

```
1. storage.Method.asyncResponse field (Jackson can parse)
     |
2. AsyncJobResponse data object in YAML (TypeRef resolves)
     |
3. domain.Method.asyncResponse field (domain model exists)
     |
4. StorageToDomainMappers handles asyncResponse (pipeline connected)
     |
5. MethodProcessor generates *AsyncResponse class (code output)
     |
6. ObjectInspectionHelper.hasAsyncResponse() (template helper ready)
     |
7. Velocity template renders asyncResponse section (HTML output)
```

Breaking this order causes cascading failures. Steps 1-2 are hard prerequisites; steps 3-4 are tightly coupled; steps 5-7 can be parallelized after 4 is complete.

## Sources

- Direct codebase analysis (all file paths referenced above)
- Jackson 3.x default behavior: `FAIL_ON_UNKNOWN_PROPERTIES` defaults to `true` (consistent with Jackson 2.x behavior, confirmed by absence of explicit override in `JacksonMapperBuilder.kt`)
- Velocity null reference behavior: Velocity prints reference text for undefined/null references by default (standard VTL behavior)
