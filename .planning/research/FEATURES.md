# Feature Landscape

**Domain:** asyncResponse block support for restdocs toolchain
**Researched:** 2026-06-02

## Table Stakes

Features that MUST work or the asyncResponse feature is broken. Grouped by spec case coverage.

### Storage Model (YAML Deserialization)

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Add `asyncResponse` field to storage `Method` | Without this, YAML with `asyncResponse` either fails to parse or silently drops the block | Low | Same type as `response: Response?`. Jackson will deserialize it if the field exists on the data class. |
| `asyncResponse` follows identical TypeSpec schema as `response` | Spec requirement: "follows the same schema rules as `response`" | None (reuse) | `Response` already extends `TypeSpec` -- reuse the same class. The field is just `val asyncResponse: Response? = null`. |

### Domain Model

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Add `asyncResponse` field to domain `Method` | Domain model is the single source of truth for codegen and rendering. Without it, downstream layers have no data. | Low | `var asyncResponse: Response? = null` alongside existing `var response: Response? = null`. |

### Mapper (Storage-to-Domain)

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Map `asyncResponse` in `StorageToDomainMappers.MethodStorageModel.mapToModel()` | Connects storage parsing to domain model. Same logic as `response?.mapToModel(context)`. | Low | One additional line: `asyncResponse = this@mapToModel.asyncResponse?.mapToModel(context)` in the `MethodStorageModel.mapToModel()` function body (line ~517 of StorageToDomainMappers.kt). |

### Validator (doc_validator) -- 4 Rules from Spec

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Rule 1: Whitelist `asyncResponse` as valid method-level key | Without this, validator rejects valid YAML. Currently unknown keys may be flagged. | Low | Jackson with `@JsonProperty` handles this automatically when the field exists on `Method`. The real issue is the two-pass validation approach -- adding the field to storage Method is sufficient for whitelisting. |
| Rule 2: Apply same structural rules to `asyncResponse` as to `response` | TypeSpec structural validation (type/typeref exclusivity, array needs items, object needs fields, etc.) must fire for asyncResponse content. | Low | Already automatic -- `Response` extends `TypeSpec` which implements `Validatable`. Jackson's validation framework calls `validate()` during deserialization on all `Validatable` instances. Adding the field to storage `Method` is sufficient. |
| Rule 3: Warn when `response` contains `job` field but `asyncResponse` is absent | Flags undocumented async payloads during migration. Must be WARNING not error to avoid breaking 49 existing methods. | Medium | Requires custom validation logic in `Method.validate()` that inspects `response.fields` for a field named `job`. New code path -- not covered by existing TypeSpec validation. Must check at FullContext pass when field resolution is available. |
| Rule 4: Error when `asyncResponse` present but `response` has no `job` field | Structural invariant: async payload without job ID is nonsensical. Must be ERROR (hard failure). | Medium | Same location as Rule 3. Inspect response fields/typeref for presence of `job`. Edge case: when response is `typeref: AsyncJobResponse` (or `AsyncCapableResponse`), must recognize these as "has job" without field-level inspection. |

### HTML Renderer (doc_generator)

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Emit asyncResponse as second table with header "Async Response (via getAsyncJobStatus)" | Spec rendering requirement. Without visible output, the feature has no user-facing value. | Medium | Modify Velocity template (`rest_api_doc.vm`) to check `$method.asyncResponse` and render a second response section. Need `ObjectInspectionHelper` method like `hasAsyncResponse(method)`. Template operates on storage model, so the field must exist there. |
| Prose note: content appears in `jr` when `jobStatus` is `0` | Spec requirement for user context. | Low | Static HTML below the async response header. |
| Render asyncResponse fields table (same format as response) | The async payload structure must be visible with field names, types, descriptions. | Low-Medium | Reuse existing `#fieldrow` macro. If asyncResponse is typeref, show the type link. If object, show inline field table. Same branching as response rendering. |

### Kotlin Generator (kotlin_generator)

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Emit `*AsyncResponse` data class for methods with `asyncResponse` block | User decision (PROJECT.md Key Decisions): generate typed async response classes. | Medium-High | `MethodProcessor.processMethod()` must check `method.asyncResponse` and generate an additional class. Class name: `${MethodName}AsyncResponse`. Uses same `ObjectProcessor.processObjectToTypeSpec()` path as sync response. When asyncResponse is a typeref, no class generation needed (already defined). |
| Handle asyncResponse typeref case (no extra class) | When `asyncResponse` uses `typeref:`, the referenced type already exists -- don't double-generate. | Low | Same logic as existing response handling in `getClassNames()`: TypeRefSpec -> use existing class, ObjectSpec -> generate new class. |

### Spec Cases Coverage

| Case | Storage | Domain | Validator | Renderer | Codegen | Notes |
|------|---------|--------|-----------|----------|---------|-------|
| Case 1 (pure async) | `response: {typeref: AsyncJobResponse}` + `asyncResponse: {type: object, fields: [...]}` | Both fields populated | Rule 4 passes (response typeref recognized as having job) | Shows sync response (typeref link) + async table | Generates `*AsyncResponse` class | Most common new pattern |
| Case 2 (mixed) | `response: {type: object, fields: [job, ...]}` + `asyncResponse: {...}` | Both fields populated | Rule 4 passes (response has job field) | Shows sync response table + async table | Generates both `*Response` and `*AsyncResponse` | Two classes per method |
| Case 3 (sync/no change) | `asyncResponse` absent | `asyncResponse` is null | Rules 3/4 not triggered (no async block, no job field) | No async section rendered | No async class generated | Backward compatible |

### AsyncJobResponse Typeref Definition

| Feature | Why Required | Complexity | Notes |
|---------|-------------|------------|-------|
| Add `AsyncJobResponse` as a canonical named type in the YAML spec | Case 1 methods need to reference it. Without it, typeref validation fails on `typeref: AsyncJobResponse`. | Low | This is a YAML content change (add to the `dataObjects` or response types section of the actual spec YAML being processed), not a toolchain code change. The toolchain user adds this to their spec file. |
| Keep `AsyncCapableResponse` as alias/existing type | Backward compatibility for 49 existing methods. | None | No code change -- just don't remove it. |

## Differentiators

Features that enhance the asyncResponse support but are not required for correctness.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Hyperlink cross-link from asyncResponse section to getAsyncJobStatus (id 68) | Better navigation in generated HTML docs | Medium | Deferred per PROJECT.md "Out of Scope". Requires knowing the anchor ID for method 68. |
| Smarter Rule 3 warning: recognize `AsyncCapableResponse` typeref as "has job" | Avoids spurious warnings on 49 existing methods during migration | Low | When `response.typeRef` is `AsyncCapableResponse` or `AsyncJobResponse`, skip the "missing asyncResponse" warning. |
| Emit `deserializeAsyncResponse` function alongside `deserializeResponse` in generated request class | Client code can deserialize the `jr` field directly into the typed async response | Medium | Adds to `MethodProcessor`. Would generate: `fun deserializeAsyncResponse(json: Json, jsonElement: JsonElement): *AsyncResponse`. Nice for SDK consumers. |
| AsyncResponse class name configuration | Allow overriding `*AsyncResponse` suffix via properties/options | Low | Pattern already exists for other naming conventions in KotlinGenerator. |
| Template-level conditional rendering for typeref vs inline object asyncResponse | When asyncResponse is `typeref: SomeType`, show it as a linked type name rather than expanding inline | Low | Mirror existing response rendering behavior. May already work if template logic is correct. |

## Anti-Features

Features to explicitly NOT build per PROJECT.md "Out of Scope" section.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Migrate all 49 existing `AsyncCapableResponse` usages | No urgency; introduces risk of breaking existing generated code | Migrate opportunistically when methods are touched for other reasons |
| Formal JSON Schema / grammar update for YAML format | Follow-on work, not blocking | Document the format change in prose (the spec doc already serves this purpose) |
| `jr` field cross-referencing / per-method typing | Architecturally complex; out of scope per spec open question #4 | Leave `jr` as `type: string` in getAsyncJobStatus |
| Client SDK stub generation from asyncResponse | Scope creep; asyncResponse is documentation + typed class, not a full async workflow SDK | Generate the data class only; leave polling/orchestration to consumer code |
| Remove `AsyncCapableResponse` type | Backward compatibility required | Keep as-is; new entries use `AsyncJobResponse` |

## Feature Dependencies

```
Storage Model (asyncResponse field on Method)
    |
    +---> Mapper (storage -> domain mapping of asyncResponse)
    |         |
    |         +---> Domain Model (asyncResponse field on domain Method)
    |                   |
    |                   +---> Kotlin Generator (*AsyncResponse class emission)
    |
    +---> Validator Rule 1 & 2 (automatic via TypeSpec.validate())
    |
    +---> Validator Rules 3 & 4 (custom logic in Method.validate())
    |
    +---> HTML Renderer (template reads storage model directly)
              |
              +---> ObjectInspectionHelper additions (hasAsyncResponse, etc.)
```

Key dependency insight: **The HTML renderer operates on the storage model**, while the Kotlin generator operates on the domain model. Both need the `asyncResponse` field, but at different layers:
- Template rendering: needs `asyncResponse` on `storage.Method`
- Code generation: needs `asyncResponse` on `domain.Method` (via mapper)
- Validation: operates during Jackson deserialization of storage model (two-pass)

This means the storage model change unblocks BOTH the validator AND the renderer simultaneously. The domain model change is only needed for the Kotlin generator.

## MVP Recommendation

### Phase 1: Storage + Validation (unblocks everything)

1. **Add `asyncResponse: Response?` to storage `Method`** -- immediate unblock for parsing
2. **Validator Rules 1 & 2** -- automatic, come free with the field addition
3. **Validator Rule 4 (error)** -- must work before users start writing asyncResponse blocks
4. **Validator Rule 3 (warning)** -- lower priority but same implementation location as Rule 4

### Phase 2: HTML Rendering

5. **Template modification** -- second response table with header
6. **ObjectInspectionHelper additions** -- `hasAsyncResponse(method: Method): Boolean`
7. **Prose note rendering** -- static content about `jr` and `jobStatus`

### Phase 3: Kotlin Code Generation

8. **Domain model addition** -- `asyncResponse` on domain `Method`
9. **Mapper addition** -- one line in `MethodStorageModel.mapToModel()`
10. **MethodProcessor changes** -- generate `*AsyncResponse` class

### Defer

- **Hyperlink cross-linking**: per PROJECT.md, deferred
- **Smart Rule 3 typeref recognition**: nice-to-have, can be added later without breaking anything
- **`deserializeAsyncResponse` function**: SDK enhancement, not core feature

## Complexity Assessment

| Component | Estimated Complexity | Rationale |
|-----------|---------------------|-----------|
| Storage model change | Trivial | Add one nullable field to data class |
| Domain model change | Trivial | Add one nullable field to class |
| Mapper change | Trivial | One additional mapping line, reuses existing `ResponseStorageModel.mapToModel()` |
| Validator Rules 1-2 | Zero (automatic) | TypeSpec validation is already recursive via Jackson |
| Validator Rules 3-4 | Medium | Custom logic needing field-name inspection and typeref recognition |
| HTML template | Medium | Velocity template modification with conditional logic, field table reuse |
| ObjectInspectionHelper | Low | One or two helper methods |
| MethodProcessor (codegen) | Medium-High | New class generation path parallel to existing response handling |
| Integration testing | Medium | Need test YAML specs covering all 3 cases for each command |

## Sources

- Primary spec: `.claude/docbuild-async-response-spec.md`
- PROJECT.md: `.planning/PROJECT.md`
- Storage Method model: `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt`
- Domain Method model: `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt`
- Mapper: `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
- MethodProcessor (codegen): `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt`
- HTML template: `rest_api_doc.vm` (root, renders methods with response)
- DocGenerator: `src/main/java/com/giffardtechnologies/restdocs/DocGenerator.kt`
- DocValidator: `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt`
