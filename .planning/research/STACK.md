# Technology Stack: asyncResponse Block Changes

**Project:** restdocs asyncResponse support
**Researched:** 2026-06-02
**Scope:** What files/classes need modification and how

## No New Dependencies Required

The asyncResponse feature is purely additive within the existing type system. The storage `Response` class already extends `TypeSpec`, which handles all the structural complexity (objects, arrays, typerefs, etc.). The domain `Response` is a simple wrapper around a `TypeSpec`. No new libraries, frameworks, or dependencies are needed.

## Changes Required By Layer

### 1. Storage Layer (Jackson Deserialization)

| File | Change | Rationale |
|------|--------|-----------|
| `src/main/java/.../storage/Method.kt` | Add `val asyncResponse: Response? = null` field | Mirrors existing `response` field; Jackson auto-deserializes from YAML |

**How it works today:** The storage `Method` data class has `val response: Response? = null`. Jackson YAML mapper (via `tools.jackson.dataformat.yaml.YAMLMapper`) deserializes the YAML key `response` directly into a `Response` instance. `Response` extends `TypeSpec` which provides the full structural type (type/typeref/fields/items/etc.). The `Validatable` interface on `TypeSpec` runs structural validation during deserialization via `ValidatingBeanDeserializerModifier`.

**What to do:** Add `val asyncResponse: Response? = null` as a new field in the `Method` data class. Jackson will pick up the `asyncResponse` YAML key automatically. No `@JsonProperty` annotation needed since the field name matches the YAML key. The `Response` class (extending `TypeSpec`) already implements `Validatable`, so structural validation (type/typeref constraints) runs automatically on the asyncResponse field during deserialization.

**Important detail:** The `Method.validate()` function currently enforces that `id` or `path` exists and checks for duplicate method names. Validation of `asyncResponse` cross-referencing (`job` field presence check, etc.) should be added here or in the mapper layer.

### 2. Domain Layer

| File | Change | Rationale |
|------|--------|-----------|
| `src/main/java/.../domain/Method.kt` | Add `var asyncResponse: Response? = null` field | Mirrors existing `response` field pattern |

**How it works today:** The domain `Method` class has `var response: Response? = null`. The domain `Response` is a data class wrapping `typeSpec: TypeSpec` and an optional `description: String?`. This is the validated, type-resolved representation.

**What to do:** Add `var asyncResponse: Response? = null` to the domain `Method` class, same type as `response`.

### 3. Mapper Layer (Storage to Domain)

| File | Change | Rationale |
|------|--------|-----------|
| `src/main/java/.../mappers/StorageToDomainMappers.kt` | Map `asyncResponse` in `MethodStorageModel.mapToModel()` | Follow exact pattern of `response` mapping |

**How it works today:** The mapper function `MethodStorageModel.mapToModel(context: Context)` at line 506 creates a domain `Method` with:
```kotlin
response = response?.mapToModel(context),
```

The `ResponseStorageModel.mapToModel(context)` function at line 534 is:
```kotlin
private fun ResponseStorageModel.mapToModel(context: Context): Response {
    return Response(
        typeSpec = this.mapToModel("response", context),
        description = description,
    )
}
```

This calls `TypeSpecStorageModel.mapToModel(typeSpecIdentifier: String, context: Context)` which recursively resolves all type structures.

**What to do:** Add to the `MethodStorageModel.mapToModel()` call:
```kotlin
asyncResponse = asyncResponse?.mapToModel(context),
```

And either reuse the existing `ResponseStorageModel.mapToModel()` or create a variant that passes `"asyncResponse"` as the identifier for better error messages:
```kotlin
private fun ResponseStorageModel.mapToAsyncResponseModel(context: Context): Response {
    return Response(
        typeSpec = this.mapToModel("asyncResponse", context),
        description = description,
    )
}
```

**Cross-validation logic** (warn when response has `job` but no asyncResponse; error when asyncResponse exists but no `job` in response) should be added either:
- In `MethodStorageModel.mapToModel()` after both fields are mapped, or
- In a separate validation pass

The mapper approach is consistent with existing patterns -- validation occurs during mapping.

### 4. Validator Layer

| File | Change | Rationale |
|------|--------|-----------|
| `src/main/java/.../storage/Method.kt` (validate function) | Add asyncResponse-specific cross-checks | Enforce spec constraints |
| `docs/reference.yaml` | Add asyncResponse to schema definition | Document the new key |

**How validation works today:** Two-pass deserialization in `DocValidator`:
1. **Pass 1 (AccumulatingContext):** Collects type names and method names. `Method.validate()` checks structural rules.
2. **Pass 2 (FullContext):** Has full type registry. `TypeSpec.validate()` verifies `typeref` references resolve.

Since `Response` extends `TypeSpec` and `TypeSpec` implements `Validatable`, the asyncResponse field will automatically be validated for structural correctness and typeref resolution in both passes.

**What to add:** In `Method.validate()`:
- **Warning** (not error): When response contains a field named `job` but `asyncResponse` is null. This requires inspecting `response.fields` which may be null if the response is a typeref.
- **Error:** When `asyncResponse` is not null but `response` has no `job` field (and response is not a known async typeref like `AsyncJobResponse`/`AsyncCapableResponse`).

### 5. HTML Generation Layer

| File | Change | Rationale |
|------|--------|-----------|
| `rest_api_doc.vm` | Add asyncResponse rendering block after response | Display the async payload docs |
| `src/main/java/.../htmlgen/ObjectInspectionHelper.kt` | Add `hasAsyncResponse(method: Method): Boolean` helper | Template conditional |

**How it works today:** The Velocity template uses the **storage model** directly (not the domain model). `DocGenerator.generateHTML()` passes the raw `storage.Document` to Velocity context. The template accesses `$method.response` where `method` is a `storage.Method`.

**What to do in the template** (after line 159 in `rest_api_doc.vm`):
```velocity
#if ($method.asyncResponse)
                    <h5>Async Response (via getAsyncJobStatus)</h5>
                    <p><em>Content appears in <code>jr</code> when <code>jobStatus</code> is <code>0</code>.</em></p>
                    <p>$link.type($method.asyncResponse)</p>
#end
```

**ObjectInspectionHelper:** Add a helper method for cleaner template conditionals. Since the template already accesses `$method.asyncResponse` directly via storage model property access, the `#if` null check suffices. But adding `hasAsyncResponse` is consistent with the existing helper pattern.

### 6. Kotlin Code Generation Layer

| File | Change | Rationale |
|------|--------|-----------|
| `src/main/java/.../codegen/MethodProcessor.kt` | Generate `*AsyncResponse` class alongside `*Response` | Spec requirement |
| `src/main/java/.../KotlinGenerator.kt` | No change needed | MethodProcessor handles it internally |

**How it works today:** `MethodProcessor.processMethod(method)` generates:
- A request class (`*Request`) with params and deserialization
- A response class (if `response.typeSpec` is `ObjectSpec`) via `createResponseClassDefinition()`
- For TypeRefSpec responses, it uses the existing class name from `typeRefPackage`

**Key logic in `getClassNames()`:**
```kotlin
val responseClassName = method.response?.let { response ->
    when (response.typeSpec) {
        is DomainTypeSpec.TypeRefSpec -> ClassName(typeRefPackage, response.typeSpec.referenceName)
        is DomainTypeSpec.ObjectSpec -> ClassName(requestsPackage, methodName + "Response")
        else -> null
    }
} ?: Unit::class.asClassName()
```

**What to do:**
1. Add similar logic for `asyncResponse` in `getClassNames()` to produce `MethodClassNames` that includes an optional async response class name (e.g., `methodName + "AsyncResponse"`).
2. In `processMethod()`, after generating the response class, generate an async response class using the same `createResponseClassDefinition()` pattern if `method.asyncResponse` is not null and its typeSpec is `ObjectSpec`.
3. For TypeRefSpec asyncResponses, no class generation needed (the referenced type already exists).

**Design choice:** The `MethodClassNames` data class should gain an optional `asyncResponseClassName: ClassName?` field, or a separate method should handle async class naming.

## Approach Summary

| Layer | Effort | Risk |
|-------|--------|------|
| Storage (Method.kt) | Trivial - add one nullable field | None |
| Domain (Method.kt) | Trivial - add one nullable field | None |
| Mapper | Low - 3-4 lines following exact existing pattern | Low - error identifier string |
| Validator | Medium - cross-field validation logic | Medium - detecting `job` in typeref responses |
| HTML Template | Low - conditional block mirroring response | None |
| Kotlin Codegen | Medium - parallel class generation | Low - follows response pattern |

## Jackson Deserialization Details

**No configuration needed.** The storage `Method` is a Kotlin data class. Jackson YAML mapper with `kotlinModule()` handles nullable properties by defaulting to `null` when absent from YAML. Since `asyncResponse` has `= null` default, existing YAML files without this key parse without error.

**Validation hook:** `Response` extends `TypeSpec` which implements `Validatable`. The `ValidatingBeanDeserializerModifier` (registered via `ValidationModule`) automatically calls `validate()` on any deserialized `Validatable` instance. This means `asyncResponse` will be structurally validated during deserialization without any additional wiring.

## Backward Compatibility

| Concern | Impact | Mitigation |
|---------|--------|------------|
| Existing YAML files without `asyncResponse` | None | Field defaults to null |
| Existing code referencing `Method` | None | New field is nullable with default |
| 49 existing `AsyncCapableResponse` usages | None | No migration required |
| Generated code consumers | None | New `*AsyncResponse` classes are additive |

## File Change Manifest

Ordered by implementation dependency:

1. **`src/main/java/.../storage/Method.kt`** - Add `val asyncResponse: Response? = null`
2. **`src/main/java/.../domain/Method.kt`** - Add `var asyncResponse: Response? = null`
3. **`src/main/java/.../mappers/StorageToDomainMappers.kt`** - Map asyncResponse in `MethodStorageModel.mapToModel()`
4. **`src/main/java/.../storage/Method.kt`** (validate) - Add cross-field validation
5. **`rest_api_doc.vm`** - Add async response rendering
6. **`src/main/java/.../htmlgen/ObjectInspectionHelper.kt`** - Add `hasAsyncResponse` helper (optional)
7. **`src/main/java/.../codegen/MethodProcessor.kt`** - Generate async response classes

## Sources

- Direct source code analysis of the restdocs codebase (HIGH confidence)
- All findings verified against actual implementation patterns in the code
