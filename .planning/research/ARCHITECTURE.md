# Architecture: asyncResponse Integration Points

**Project:** restdocs asyncResponse block support
**Researched:** 2026-06-02
**Confidence:** HIGH (based on direct source code analysis)

## Critical Discovery: Two Parallel Pipelines

The restdocs toolchain has **two independent rendering pipelines** that consume different models:

| Pipeline | Model Consumed | Entry Point |
|----------|---------------|-------------|
| **HTML doc generation** | `storage.Document` (raw YAML deserialization) | `DocGenerator.generateHTML()` |
| **Kotlin code generation** | `domain.Document` (mapped via `StorageToDomainMappers`) | `KotlinGenerator.generate()` |

This means `asyncResponse` must be added to **both** the storage model (for HTML) and the domain model (for codegen), with a mapper bridging them. The HTML renderer uses storage objects directly in Velocity templates -- it does NOT use the domain model.

## Layer-by-Layer Integration Points

### Layer 1: Storage Model (YAML Deserialization)

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt`

**Current state:** The `Method` data class has `val response: Response? = null` (line 50).

**Integration point:** Add a new field:
```kotlin
val asyncResponse: Response? = null,
```

The existing `storage.Response` class (extends `storage.type.TypeSpec`) already handles all structural variations (type, typeref, fields, items, etc.). No new type is needed -- `asyncResponse` has the same schema as `response`.

**Jackson annotation:** The field name `asyncResponse` maps directly to the YAML key `asyncResponse` with no annotation needed (Jackson's default camelCase handling). If the YAML uses a different form, add `@JsonProperty("asyncResponse")`.

**Validation in `Method.validate()`:** No change needed here because structural validation of the response content is handled by `TypeSpec.validate()` which is called automatically by Jackson's `Validatable` mechanism during deserialization. The `asyncResponse` field, being a `TypeSpec` subclass, will validate itself.

---

### Layer 2: Storage Validation (DocValidator two-pass)

**File:** `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt`

**Current state:** DocValidator performs two passes. The `TypeSpec.validate()` method (in `storage.type.TypeSpec`) handles structural validation for any TypeSpec including Response.

**Integration point:** Cross-field validation (the spec requires):
1. **Warning:** `response` has a `job` field but no `asyncResponse` -- add to `Method.validate()` or a new post-deserialization check.
2. **Error:** `asyncResponse` present but `response` has no `job` field -- add to `Method.validate()`.

Because `storage.Method.validate()` already receives a `validationContext` and has access to its own fields, this is the natural location. Add the cross-field checks after the existing parameter validation:

```kotlin
// In Method.validate():
if (asyncResponse != null && response != null) {
    val hasJobField = response.fields?.any { it.name == "job" } == true
    val isAsyncJobTypeRef = response.typeRef in listOf("AsyncJobResponse", "AsyncCapableResponse")
    if (!hasJobField && !isAsyncJobTypeRef) {
        throw ValidationException("Method '$name' has asyncResponse but response has no 'job' field")
    }
}
if (asyncResponse == null && response != null) {
    val hasJobField = response.fields?.any { it.name == "job" } == true
    if (hasJobField) {
        // Warning only -- do not throw
        // Needs a warning mechanism (currently only exceptions exist)
    }
}
```

**Warning mechanism gap:** The current validator only uses exceptions (hard errors). A warning for "has job but no asyncResponse" would require either:
- A new warning accumulation mechanism (passed via `validationContext`), or
- Printing to stderr/stdout during validation (simpler, matches existing `println` usage)

---

### Layer 3: Domain Model

**File:** `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt`

**Current state:** `var response: Response? = null` (line 16).

**Integration point:** Add:
```kotlin
var asyncResponse: Response? = null,
```

The domain `Response` is a simple wrapper: `data class Response(val typeSpec: TypeSpec, val description: String? = null)`. It already supports any `TypeSpec` variant (ObjectSpec, TypeRefSpec, ArraySpec, etc.), so `asyncResponse` needs no new domain types.

---

### Layer 4: Mapper (Storage to Domain)

**File:** `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`

**Current state:** The `MethodStorageModel.mapToModel(context)` function (line 506-523) maps `response` via:
```kotlin
response = response?.mapToModel(context),
```

Which calls `ResponseStorageModel.mapToModel(context)` (line 534-539):
```kotlin
private fun ResponseStorageModel.mapToModel(context: Context) : Response {
    return Response(
        typeSpec = this.mapToModel("response", context),
        description = description,
    )
}
```

**Integration point:** In `MethodStorageModel.mapToModel()`, add:
```kotlin
asyncResponse = asyncResponse?.mapToModel(context),
```

The same `ResponseStorageModel.mapToModel()` function handles both -- but note it currently passes `"response"` as the `typeSpecIdentifier` for error messages. For `asyncResponse`, pass `"asyncResponse"` instead. This means either:

**Option A (simple):** Inline the call with a different identifier:
```kotlin
asyncResponse = this.asyncResponse?.let {
    Response(
        typeSpec = it.mapToModel("asyncResponse", context),
        description = it.description,
    )
},
```

**Option B (extract helper):** Parameterize `ResponseStorageModel.mapToModel()`:
```kotlin
private fun ResponseStorageModel.mapToModel(identifier: String, context: Context): Response {
    return Response(
        typeSpec = this.mapToModel(identifier, context),
        description = description,
    )
}
```

Option B is cleaner. The existing call for `response` becomes `response?.mapToModel("response", context)`.

---

### Layer 5: HTML Generation (Velocity Template)

**Files:**
- `rest_api_doc.vm` (root-level, simpler template with methods section)
- `docs/rest_api_doc.vm` (full-featured template, data objects and enums only -- no methods section)
- `src/main/java/com/giffardtechnologies/restdocs/htmlgen/ObjectInspectionHelper.kt`

**Critical observation:** The root-level `rest_api_doc.vm` renders methods (lines 129-176), including the response at line 159:
```velocity
<h5>Response</h5>
<p>$link.type($method.response)</p>
```

The `docs/rest_api_doc.vm` does NOT render methods at all -- it only covers data objects and enumerations.

**Integration point (template):** After the response section in `rest_api_doc.vm`, add:
```velocity
#if ($method.asyncResponse)
                    <h5>Async Response (via getAsyncJobStatus)</h5>
                    <p class="async-note">Content appears in <code>jr</code> field when <code>jobStatus</code> is <code>0</code>.</p>
                    <p>$link.type($method.asyncResponse)</p>
#end
```

**Integration point (ObjectInspectionHelper):** Add a helper method:
```kotlin
fun hasAsyncResponse(method: Method): Boolean {
    return method.asyncResponse != null
}
```

However, the current template accesses `$method.asyncResponse` directly (Velocity resolves bean properties). Since `asyncResponse` is a nullable property on `storage.Method`, Velocity's `#if ($method.asyncResponse)` will evaluate to false when null. No helper strictly needed for the null check, but one may be useful for the field-table rendering that the spec requests.

**For field-level rendering** (if `asyncResponse` has inline fields), the template needs the same `#fieldrow` macro treatment as response fields. The existing template renders response as a simple `$link.type()` call, which outputs a text description. For the richer table rendering described in the spec, a more detailed template block is needed -- similar to how parameters are rendered with `#fieldrow`.

---

### Layer 6: Kotlin Code Generation (MethodProcessor)

**File:** `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt`

**Current state:**
- `getClassNames()` (line 177-197) derives `responseClassName` from `method.response.typeSpec`
- `processMethod()` (line 204-378) generates request class + response class
- `createResponseClassDefinition()` (line 393-411) creates the TypeSpec for ObjectSpec responses

**Integration point:** After generating the regular response class, generate an async response class:

1. **In `getClassNames()`** -- add `asyncResponseClassName`:
```kotlin
data class MethodClassNames(
    val requestClassName: ClassName,
    val responseClassName: ClassName,
    val asyncResponseClassName: ClassName? = null,  // NEW
)
```

The async class name follows the pattern `*AsyncResponse`:
```kotlin
val asyncResponseClassName = method.asyncResponse?.let { asyncResponse ->
    when (asyncResponse.typeSpec) {
        is DomainTypeSpec.TypeRefSpec -> ClassName(typeRefPackage, asyncResponse.typeSpec.referenceName)
        is DomainTypeSpec.ObjectSpec -> ClassName(requestsPackage, methodName + "AsyncResponse")
        else -> null
    }
}
```

2. **In `processMethod()`** -- after writing the response class, generate the async response class:
```kotlin
val asyncResponseClassTypeSpec = method.asyncResponse?.let { asyncResponse ->
    createResponseClassDefinition(asyncResponse, asyncResponseClassName!!)
}
```

The `createResponseClassDefinition()` private method already handles this -- it takes a `Response?` and a `ClassName`, delegates to `objectProcessor.processObjectToTypeSpec()` for ObjectSpec, and returns null for TypeRefSpec (which already has a class definition elsewhere). It can be reused directly.

3. **In the file writing** -- include the async response TypeSpec in the same file:
```kotlin
file(requestClassName) {
    addType(requestClassBuilder.build())
    responseClassTypeSpec?.let { addType(it) }
    asyncResponseClassTypeSpec?.let { addType(it) }  // NEW
}.writeTo(codeDirectory)
```

---

### Layer 7: KotlinGenerator (SwiftAPIServerClient)

**File:** `src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt`

**Current state:** The `SwiftAPIServerClient` class generates `execute()`, `executeBlocking()`, and `executeForResult()` methods that return `responseClassName`.

**Integration point:** If a method has `asyncResponse`, optionally generate an additional helper that returns the async response type. However, per the spec's "Out of Scope" section, client SDK stub generation from asyncResponse is explicitly out of scope. **No changes needed here for MVP.**

---

## Data Flow: YAML to Output

### HTML Documentation Flow
```
YAML file
  --> Jackson deserialization (with Validatable validation)
  --> storage.Document (storage.Method now has asyncResponse: Response?)
  --> Velocity template context
  --> Template checks $method.asyncResponse, renders second response table
  --> HTML output
```

### Kotlin Code Generation Flow
```
YAML file
  --> Jackson deserialization
  --> storage.Document
  --> StorageToDomainMappers.mapToModel()
      --> MethodStorageModel.mapToModel() maps asyncResponse via ResponseStorageModel.mapToModel()
  --> domain.Document (domain.Method now has asyncResponse: Response?)
  --> MethodProcessor.processMethod()
      --> getClassNames() derives asyncResponseClassName
      --> createResponseClassDefinition() generates TypeSpec for async response
  --> KotlinPoet writes *AsyncResponse data class
```

## Build Order (Strict Dependencies)

```
Phase 1: Storage Model
    storage.Method adds asyncResponse field
    |
    v
Phase 2: Storage Validation (can run in parallel with Phase 3)
    Method.validate() adds cross-field checks
    TypeSpec.validate() already covers structural validation automatically
    |
    v
Phase 3: Domain Model
    domain.Method adds asyncResponse field
    |
    v
Phase 4: Mapper
    StorageToDomainMappers maps asyncResponse field
    (depends on both storage and domain having the field)
    |
    +-----------+-----------+
    |                       |
    v                       v
Phase 5a: HTML Template    Phase 5b: Kotlin Codegen
    Velocity template          MethodProcessor generates
    renders asyncResponse      *AsyncResponse class
```

**Minimum viable ordering:**
1. `storage.Method` -- must come first (everything depends on deserialization)
2. `domain.Method` + `StorageToDomainMappers` -- can be done together (mapper depends on both models)
3. Validation enhancements in `Method.validate()` -- can be done after storage model exists
4. HTML template and Kotlin codegen -- independent of each other, both depend on their respective model layers being complete

## Shared Logic Between response and asyncResponse

### Already Shared (no extraction needed)
- `storage.Response` class -- reused directly for both fields
- `storage.type.TypeSpec.validate()` -- validates both automatically via Jackson
- `domain.Response` data class -- reused directly
- `ResponseStorageModel.mapToModel()` in mapper -- called for both (with parameterized identifier)
- `createResponseClassDefinition()` in MethodProcessor -- called for both
- `objectProcessor.processObjectToTypeSpec()` -- handles both response object types

### Extraction Opportunity
The mapper's response mapping currently has a hard-coded `"response"` string for error messages. Parameterize it to accept `"response"` or `"asyncResponse"` -- this is a trivial refactor (one parameter addition).

### No Extraction Needed
There is no response-specific logic that needs to be factored out into a shared utility. The existing abstractions (`Response`, `TypeSpec`, `ObjectProcessor`) are already generic enough to handle both `response` and `asyncResponse` with zero duplication.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Creating a Separate AsyncResponse Storage Class
**Why bad:** `asyncResponse` has identical schema to `response`. Creating a separate class would duplicate TypeSpec, Jackson annotations, and validation logic.
**Instead:** Reuse `storage.Response` directly (it is already a TypeSpec subclass with a description).

### Anti-Pattern 2: Mapping asyncResponse Through a Different Mapper Path
**Why bad:** The same `TypeSpec.mapToModel()` handles all type structures. A separate path would diverge and miss future TypeSpec additions.
**Instead:** Call the same `ResponseStorageModel.mapToModel(identifier, context)` with a different identifier string.

### Anti-Pattern 3: Modifying the Domain Model to "Detect" Async Methods
**Why bad:** Adding `val isAsync: Boolean` or similar computed properties creates implicit state that can drift from the source of truth (`asyncResponse != null`).
**Instead:** Consumers check `method.asyncResponse != null` directly.

### Anti-Pattern 4: Rendering asyncResponse in the docs/rest_api_doc.vm Template
**Why bad:** That template only covers data objects and enumerations. The methods section is in the root-level `rest_api_doc.vm`.
**Instead:** Modify only `rest_api_doc.vm` (or whichever template renders the methods section for the target documentation build).

## Key Architecture Invariants

1. **Storage model is YAML-shaped.** Fields map 1:1 to YAML keys. `asyncResponse` is a sibling of `response` in YAML, so it is a sibling field in `storage.Method`.

2. **Domain model is semantically enriched.** Types are resolved, fields are validated. But the structure mirrors storage closely for Method -- it is not a radical transformation.

3. **Validation happens at two levels:**
   - Structural: During Jackson deserialization via `Validatable.validate()` (automatic)
   - Cross-field/semantic: In `Method.validate()` (manual checks)

4. **HTML generation uses storage model directly.** The Velocity template accesses `storage.Method` properties. This means `asyncResponse` must be accessible as a property on `storage.Method` for the template to use it.

5. **Kotlin codegen uses domain model.** The `MethodProcessor` receives `domain.Method`. The async response class generation must read from `domain.Method.asyncResponse`.

## Sources

- Direct source code analysis of all files listed above
- `.planning/codebase/ARCHITECTURE.md` for layer overview
- `.claude/docbuild-async-response-spec.md` for requirements
