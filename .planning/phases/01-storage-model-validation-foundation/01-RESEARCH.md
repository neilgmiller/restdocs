# Phase 1: Storage Model + Validation Foundation - Research

**Researched:** 2026-06-02
**Domain:** Kotlin data model extension + Jackson 3.x deserialization + validation pipeline
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Extend `Validatable` with a 2-arg overload: `fun validate(validationContext: Any?, warningEmitter: (String) -> Unit)`. Default implementation delegates to 1-arg `validate(validationContext)`. Existing implementations require zero changes.
- **D-02:** Thread `warningEmitter: (String) -> Unit` as a **separate constructor parameter** through `ValidationModule` → `ValidatingBeanDeserializerModifier` → `ValidatingDeserializer` → `validate()`. NOT added to `AccumulatingContext` or `FullContext`.
- **D-03:** `DocValidator` creates mappers with a `warningEmitter`. Exact surface (stderr vs. `messageHandler` callback) is planner discretion.
- **D-04:** `storage.Method` overrides 2-arg `validate(context, warningEmitter)`. Only `Validatable` needing the override for this phase.
- **D-05:** VALID-04 (asyncResponse present but response has no job field): throw `ValidationException` from `storage.Method.validate()`.
- **D-06:** VALID-03 (response has job field but asyncResponse absent): call `warningEmitter("WARNING: ...")`.
- **D-07:** "Has job field" = `response.typeRef` in `{"AsyncJobResponse", "AsyncCapableResponse"}` OR `response.fields` contains an element with `name == "job"`.
- **D-08:** Reuse existing `storage.Response` / `domain.Response` verbatim. No new subclasses.

### Claude's Discretion

- Exact warning message text for VALID-03 and VALID-04 errors
- Whether `warningEmitter` is routed to `System.err` or the existing `messageHandler` in `DocValidator`
- Order of checks within `storage.Method.validate()` (VALID-04 error vs. VALID-03 warning)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FOUND-01 | `storage.Method` has `asyncResponse: Response? = null`; YAML with `asyncResponse` parses without `UnrecognizedPropertyException` | Jackson 3.x data class field addition is sufficient; no annotation needed since key name matches field name |
| FOUND-02 | `domain.Method` has `asyncResponse: Response? = null` of type `domain.Response` | Constructor parameter addition; `domain.Method` is a plain class, not a data class |
| FOUND-03 | `StorageToDomainMappers` wires `asyncResponse` using `response?.mapToModel(context)` pattern | One-liner addition to `MethodStorageModel.mapToModel()` at line 506 |
| VALID-01 | `asyncResponse` recognized as valid method-level YAML key | Satisfied automatically by FOUND-01 (Jackson binds known fields; unknown keys throw due to `FAIL_ON_UNKNOWN_PROPERTIES=true`) |
| VALID-02 | Same structural rules as `response` apply to `asyncResponse` | Satisfied for free by reusing `ResponseStorageModel.mapToModel(context)` — same validation path |
| VALID-03 | Warning when response has job field but asyncResponse absent | 2-arg `validate()` override in `storage.Method`; `warningEmitter` call |
| VALID-04 | Error when asyncResponse present but response has no job field | 2-arg `validate()` override in `storage.Method`; `ValidationException` throw |

</phase_requirements>

---

## Summary

Phase 1 is a surgical extension of the existing Jackson deserialization and validation pipeline. The codebase already knows how to deserialize `Response` objects and thread them through the storage-to-domain mapper; `asyncResponse` rides the same tracks with no new infrastructure needed beyond adding field declarations and wiring the existing `mapToModel` call.

The only genuinely new infrastructure is the `warningEmitter` channel. Currently `Validatable.validate()` can only throw errors; it has no way to emit non-fatal diagnostics. The 2-arg overload adds this capability without breaking the six+ existing `Validatable` implementations, because the new method carries a default body that calls the 1-arg form.

Jackson 3.x `FAIL_ON_UNKNOWN_PROPERTIES` is left at its default of `true` and is not overridden anywhere in this project. This means FOUND-01 (adding the storage field) is a hard prerequisite before any YAML file with `asyncResponse` can be parsed — the storage model change must be committed before any round-trip test can run.

**Primary recommendation:** Implement in dependency order: storage field → domain field → mapper → Validatable 2-arg overload → ValidationModule chain → storage.Method.validate() → DocValidator wiring.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| YAML parsing of `asyncResponse` | Storage model (Jackson) | — | Jackson binds YAML keys to `storage.Method` fields at deserialize time |
| Structural validation of `asyncResponse` contents | Storage→Domain mapper | — | `ResponseStorageModel.mapToModel(context)` throws on invalid field defs, unresolved typerefs; reused verbatim |
| Cross-field validation (VALID-03/04) | `storage.Method.validate()` | — | Only place where both `response` and `asyncResponse` are visible simultaneously |
| Warning emission channel | `ValidatingDeserializer` → `validate()` | `DocValidator` | `warningEmitter` created in `DocValidator`, threaded through `ValidationModule` chain, called at validate time |
| Domain model propagation | `StorageToDomainMappers` | — | Maps storage→domain; no validation logic here |

---

## Standard Stack

No new external libraries are added in Phase 1. All work uses the existing stack.

### Existing Libraries In Play

| Library | Version | Role in This Phase |
|---------|---------|-------------------|
| Jackson 3.x (`tools.jackson`) | 3.1.0 | YAML deserialization of `asyncResponse` field on `storage.Method` |
| Jackson Kotlin module | 3.1.0 | Data class constructor binding for `storage.Method` |
| Kotlin stdlib | 2.1.20 | Default interface methods (2-arg `Validatable` overload) |

**No new packages to install.**

---

## Package Legitimacy Audit

Not applicable — Phase 1 adds no new dependencies.

---

## Architecture Patterns

### System Architecture Diagram

```
YAML file
    │
    ▼
YAMLMapper.readValue()                    ← Pass 1: AccumulatingContext
    │  [ValidationModule registered]
    │  [FAIL_ON_UNKNOWN_PROPERTIES = true]
    │
    ▼
ValidatingDeserializer.deserialize()
    │  deserializes storage.Method
    │  calls method.validate(accumulatingContext, warningEmitter)
    │        └─ 2-arg default → 1-arg (AccumulatingContext branch: check name uniqueness)
    │
    ▼
storage.Method  ← asyncResponse: Response? = null  [NEW FIELD]
    │
    ▼
YAMLMapper.readValue()                    ← Pass 2: FullContext
    │
    ▼
ValidatingDeserializer.deserialize()
    │  calls method.validate(fullContext, warningEmitter)
    │        └─ 2-arg override: responseHasJobField check
    │               ├─ asyncResponse != null && !hasJobField → throw ValidationException (VALID-04)
    │               └─ hasJobField && asyncResponse == null → warningEmitter("WARNING: ...") (VALID-03)
    │
    ▼
DocumentStorageModel.mapToModel()
    │
    ▼
MethodStorageModel.mapToModel(context)
    │  response = response?.mapToModel(context)
    │  asyncResponse = asyncResponse?.mapToModel(context)  [NEW LINE]
    │
    ▼
domain.Method  ← asyncResponse: Response? = null  [NEW FIELD]
```

### Recommended Project Structure

No structural changes. All modifications are in-place edits to existing files:

```
src/main/java/com/giffardtechnologies/restdocs/
├── jackson/
│   └── validation/
│       ├── Validatable.kt                    ← add 2-arg overload with default
│       ├── ValidationModule.kt               ← add warningEmitter constructor param
│       ├── ValidatingBeanDeserializerModifier.kt ← thread warningEmitter to createDelegate
│       └── ValidatingDeserializer.kt         ← thread warningEmitter; call 2-arg validate()
├── storage/
│   └── Method.kt                             ← add asyncResponse field; override validate()
├── domain/
│   └── Method.kt                             ← add asyncResponse constructor param
├── mappers/
│   └── StorageToDomainMappers.kt             ← one line: asyncResponse = asyncResponse?.mapToModel(context)
└── DocValidator.kt                           ← pass warningEmitter to createMapper()
```

### Pattern 1: Kotlin Default Interface Method (2-arg Validatable)

**What:** Add a second `validate` overload with a default body that delegates to the 1-arg form.
**When to use:** Extending an interface with new capabilities while preserving backward compatibility with all existing implementations.

```kotlin
// Source: [VERIFIED from codebase — Validatable.kt]
interface Validatable {
    fun validate(validationContext: Any?)

    fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
        validate(validationContext)
    }
}
```

All existing implementations (`storage.Method`, `storage.DataObject`, `storage.FieldListIncludeElement`, etc.) inherit the default and require no changes.

### Pattern 2: Constructor Parameter Threading

**What:** Add `warningEmitter` as a constructor parameter at each level of the chain, defaulting to a no-op lambda.
**When to use:** Injecting a dependency through a fixed construction chain where intermediate classes don't need to know the parameter's semantics.

```kotlin
// Source: [VERIFIED from codebase — ValidationModule.kt, ValidatingBeanDeserializerModifier.kt]

// ValidationModule.kt — existing pattern extended:
class ValidationModule(
    validationContext: Any? = null,
    warningEmitter: (String) -> Unit = {},
) : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier(validationContext, warningEmitter))
    }
}

// ValidatingBeanDeserializerModifier.kt — existing pattern extended:
class ValidatingBeanDeserializerModifier(
    private val validationContext: Any?,
    private val warningEmitter: (String) -> Unit,
) : ValueDeserializerModifier() {
    // ...
    private fun createDelegate(type: JavaType, target: ValueDeserializer<*>): ValueDeserializer<*> {
        return ValidatingDeserializer(target, validationContext, warningEmitter)
    }
}

// ValidatingDeserializer.kt — existing deserialize() updated:
class ValidatingDeserializer(
    private val _delegatee: ValueDeserializer<*>,
    private val validationContext: Any?,
    private val warningEmitter: (String) -> Unit,
) : StdDeserializer<Any?>(...) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any? {
        val deserializedObject: Any = _delegatee.deserialize(p, ctxt) ?: return null
        if (deserializedObject is Validatable) {
            try {
                deserializedObject.validate(validationContext, warningEmitter)
            } catch (e: Exception) {
                throw ctxt.instantiationException(deserializedObject::class.java, e)
            }
        }
        return deserializedObject
    }
    // replaceDelegatee and createContextual must also pass warningEmitter when constructing new instances
}
```

**Critical:** `ValidatingDeserializer` has two other factory sites that construct new instances — `createContextual()` (line 36-46) and `replaceDelegatee()` (line 48-54). Both must thread `warningEmitter` to the new instance, otherwise the emitter will be dropped on contextualization.

### Pattern 3: storage.Method Cross-Field Validation

**What:** Override the 2-arg `validate()` in `storage.Method` to run cross-field checks in the FullContext pass.
**When to use:** When validation requires multiple fields to be resolved simultaneously (both `response` and `asyncResponse` must be available).

```kotlin
// Source: [VERIFIED from codebase — Method.kt existing validate() + D-04/05/06/07]
override fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
    // run existing 1-arg checks (path/id, name uniqueness, parameter duplicates)
    validate(validationContext)

    // cross-field checks only meaningful in the FullContext pass
    if (validationContext !is DocValidator.AccumulatingContext) {
        if (asyncResponse != null && !responseHasJobField(response)) {
            throw ValidationException(
                "Method '$name' has asyncResponse but response has no 'job' field"
            )
        }
        if (responseHasJobField(response) && asyncResponse == null) {
            warningEmitter("WARNING: Method '$name' response has a job field but no asyncResponse block is defined")
        }
    }
}

private fun responseHasJobField(response: Response?): Boolean {
    if (response == null) return false
    if (response.typeRef in setOf("AsyncJobResponse", "AsyncCapableResponse")) return true
    return response.fields?.any { it is Field && it.name == "job" } == true
}
```

**Note on `response.fields` element types:** `storage.Response` extends `TypeSpec`. `TypeSpec.fields` is `ArrayList<FieldListElement>?`. `FieldListElement` is a sealed/abstract type — concrete subtypes include `Field` and `FieldListIncludeElement`. The `name == "job"` check must cast or smart-cast to `Field` to access `.name`. The `is Field` check in the lambda is the correct approach.

### Pattern 4: storage.Method asyncResponse Field Addition

```kotlin
// Source: [VERIFIED from codebase — Method.kt]
// Add to the data class constructor, after the existing `response` field:
val response: Response? = null,
val asyncResponse: Response? = null,   // NEW — no @JsonProperty annotation needed
```

No `@JsonProperty` annotation is required — the YAML key `asyncResponse` matches the Kotlin property name exactly. Contrast with `response`-adjacent fields that use snake-case YAML keys like `"authentication required"` which do need `@JsonProperty`.

### Pattern 5: domain.Method asyncResponse Field Addition

```kotlin
// Source: [VERIFIED from codebase — domain/Method.kt]
// domain.Method is NOT a data class — it's a plain class with a constructor.
// Add asyncResponse as a constructor parameter after response:
class Method(
    // ... existing params ...
    var response: Response? = null,
    var asyncResponse: Response? = null,   // NEW
    // ... remaining params ...
)
```

### Pattern 6: Mapper asyncResponse Wiring

```kotlin
// Source: [VERIFIED from codebase — StorageToDomainMappers.kt line 506-523]
// In MethodStorageModel.mapToModel(context):
private fun MethodStorageModel.mapToModel(context: Context): Method {
    return Method(
        // ... existing fields ...
        response = response?.mapToModel(context),
        asyncResponse = asyncResponse?.mapToModel(context),   // NEW — identical pattern
        // ...
    )
}
```

### Pattern 7: DocValidator warningEmitter Wiring

```kotlin
// Source: [VERIFIED from codebase — DocValidator.kt]
// getValidatedDocument creates two mappers; both need warningEmitter:
fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}): Document {
    val warningEmitter: (String) -> Unit = { message -> messageHandler(message) }
    // — OR — route to stderr:
    // val warningEmitter: (String) -> Unit = { message -> System.err.println(message) }

    val mapper = createMapper(AccumulatingContext(validationOptions), warningEmitter)
    // ...
    val contextMapper = createMapper(FullContext(referencableTypes, document, validationOptions), warningEmitter)
    // ...
}
```

The planner should choose `messageHandler` routing (D-03 discretion), which keeps warnings visible to callers of `getValidatedDocument` (e.g., the `DocValidatorCommand`).

### Pattern 8: createMapper Signature Extension

```kotlin
// Source: [VERIFIED from codebase — JacksonMapperBuilder.kt]
fun createMapper(validationContext: Any? = null, warningEmitter: (String) -> Unit = {}): YAMLMapper {
    val booleanModule = SimpleModule().addDeserializer(Boolean::class.java, YesNoBooleanDeserializer())
    return YAMLMapper.builder()
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .addModule(kotlinModule())
        .addModule(booleanModule)
        .addModule(ValidationModule(validationContext, warningEmitter))
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()
}
```

Default `warningEmitter = {}` preserves backward compatibility for all existing callers of `createMapper` (e.g., `DocGenerator`, `KotlinGenerator`).

### Anti-Patterns to Avoid

- **Threading warningEmitter through AccumulatingContext or FullContext:** Explicitly rejected by D-02. The context classes are concerned with type resolution state, not warning output. Mixing them creates a coupling that makes the context hard to reuse.
- **Adding a new Response subclass for asyncResponse:** Rejected by D-08. `storage.Response` and `domain.Response` are structurally identical requirements — a subclass adds no value and doubles the mapper surface area.
- **Running VALID-03/04 in the AccumulatingContext pass:** The `typeRef` check (part of D-07) requires knowing whether a typeRef like `AsyncJobResponse` is a referenceable type name — that resolution is only safe in the FullContext pass. The existing `else` branch in `storage.Method.validate()` is the correct location.
- **Forgetting `replaceDelegatee()` and `createContextual()` in ValidatingDeserializer:** These two methods also construct new `ValidatingDeserializer` instances. If `warningEmitter` is not threaded through them, deserialization of nested objects (e.g., `asyncResponse` inside `Method`) will use a no-op emitter even when a real one was provided.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| asyncResponse structural validation | Custom field-by-field checker | `ResponseStorageModel.mapToModel(context)` | Already validates type/typeref/fields/items/values; reusing it is VALID-02 for free |
| Response parsing from YAML | Manual YAML tree traversal | Jackson data class binding | Jackson 3.x with `FAIL_ON_UNKNOWN_PROPERTIES=true` handles unknown keys as errors automatically |
| Warning channel | Logging framework, custom callback type | `(String) -> Unit` lambda | Kotlin function type is sufficient; no need for a named interface or logger |

**Key insight:** The existing `mapToModel(context)` call already does deep validation of any `Response`-typed object. Applying it to `asyncResponse` gives VALID-02 at zero incremental cost.

---

## Common Pitfalls

### Pitfall 1: ValidatingDeserializer contextualization drops warningEmitter

**What goes wrong:** `ValidatingDeserializer` has three places that construct a new `ValidatingDeserializer` instance: the primary constructor call site, `createContextual()`, and `replaceDelegatee()`. If only the constructor call site threads `warningEmitter`, Jackson may replace the deserializer via contextualization and the emitter is silently dropped.

**Why it happens:** Jackson calls `createContextual()` during mapper setup to specialize deserializers for specific property types. The existing code correctly threads `validationContext` through this path — `warningEmitter` must follow the same pattern.

**How to avoid:** Update `createContextual()` (line 36-46) and `replaceDelegatee()` (line 48-54) to pass `warningEmitter` when constructing new `ValidatingDeserializer` instances.

**Warning signs:** VALID-03 warnings are not emitted during the second pass even though the conditions are met.

### Pitfall 2: VALID-03/04 firing in AccumulatingContext pass

**What goes wrong:** VALID-04 throws `ValidationException` during the first pass where `typeRef` resolution is not meaningful. `AsyncJobResponse` may not be known yet, causing `responseHasJobField()` to return `false` incorrectly.

**Why it happens:** The `typeRef` check in D-07 (`response.typeRef in {"AsyncJobResponse", "AsyncCapableResponse"}`) is a string comparison only — it does not need resolution. However, the existing code comment and pattern establish that the `else` branch (non-AccumulatingContext) is the correct place for full-context checks.

**How to avoid:** Gate both VALID-03 and VALID-04 behind `if (validationContext !is DocValidator.AccumulatingContext)`. This matches the existing `parameters?.validateHasNoDuplicates(validationContext.documentIfAvailable)` pattern in the current `validate()`.

**Warning signs:** Spurious `ValidationException` on first-pass run, before FullContext is built.

### Pitfall 3: `response.fields` contains non-Field elements

**What goes wrong:** `TypeSpec.fields` is `ArrayList<FieldListElement>?`. `FieldListElement` has concrete subtypes (at minimum `Field` and `FieldListIncludeElement`). Calling `.name` directly without a type check throws `ClassCastException` or doesn't compile.

**Why it happens:** `FieldListIncludeElement` has an `include: String` (the referenced type name), not a `name` field. The compiler will reject `.name` on a bare `FieldListElement`.

**How to avoid:** Use `response.fields?.any { it is Field && it.name == "job" }` in `responseHasJobField()`. This is type-safe and handles mixed field lists correctly.

**Warning signs:** Compilation error on the `responseHasJobField` helper; or, if using unsafe casts, `ClassCastException` at runtime on methods that use `include:` syntax in their response fields.

### Pitfall 4: `domain.Method` constructor parameter position

**What goes wrong:** `domain.Method` is a plain class (not a data class). Its constructor has 13 parameters. Adding `asyncResponse` in the wrong position relative to named call sites will silently pass the wrong value to the wrong parameter in any positional call site.

**Why it happens:** Kotlin allows both named and positional construction. There are no positional call sites in `StorageToDomainMappers.kt` (all params are named), but care is needed to add `asyncResponse` with a default of `null` so any future positional usage doesn't shift.

**How to avoid:** Add `asyncResponse: Response? = null` with an explicit default value, placed logically after `response`. Use the named parameter form in `MethodStorageModel.mapToModel()`.

### Pitfall 5: Jackson data class field ordering affects deserialization contract

**What goes wrong:** `storage.Method` is a `data class`. Adding `asyncResponse` anywhere in the constructor parameter list is fine for deserialization (Jackson uses property names, not position). However, the `copy()` method generated by the compiler uses positional parameters. Any existing test code calling `method.copy(...)` with positional arguments will silently break.

**Why it happens:** Kotlin `data class` `copy()` is positional under the hood for default argument resolution.

**How to avoid:** Search for `method.copy(` in the codebase before adding the field. (No such calls exist in the current codebase — confirmed by code inspection.) Place the field after `response` for logical grouping.

---

## Code Examples

### Full responseHasJobField helper

```kotlin
// Source: [VERIFIED from codebase — storage/Response.kt, storage/type/FieldListElement, D-07]
private fun responseHasJobField(response: Response?): Boolean {
    if (response == null) return false
    if (response.typeRef in setOf("AsyncJobResponse", "AsyncCapableResponse")) return true
    return response.fields?.any { it is Field && it.name == "job" } == true
}
```

`Field` here is `com.giffardtechnologies.restdocs.storage.type.Field` (already imported in `Method.kt`). `FieldListElement` is the abstract supertype — `Field` extends it.

### Full 2-arg validate() override for storage.Method

```kotlin
// Source: [VERIFIED from codebase — existing validate() + D-04/05/06/07]
override fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
    validate(validationContext)   // run all existing 1-arg checks unchanged

    if (validationContext !is DocValidator.AccumulatingContext) {
        if (asyncResponse != null && !responseHasJobField(response)) {
            throw ValidationException(
                "Method '$name': asyncResponse is present but response has no 'job' field"
            )
        }
        if (responseHasJobField(response) && asyncResponse == null) {
            warningEmitter("WARNING: Method '$name': response has a job field but no asyncResponse block")
        }
    }
}
```

### Mapper asyncResponse addition (one line)

```kotlin
// Source: [VERIFIED from codebase — StorageToDomainMappers.kt line 517]
private fun MethodStorageModel.mapToModel(context: Context): Method {
    return Method(
        method = this.method?.toModel() ?: Method.HTTPMethod.POST,
        path = path,
        protocolsAllowed = Array.ofAll(protocolsAllowed),
        id = id,
        name = name,
        isAuthenticationRequired = isAuthenticationRequired,
        parameterElementList = FieldElementList(parameters.mapList { it.mapToModel(context) }),
        failureCodes = Array.ofAll(failureCodes),
        successCodes = Array.ofAll(successCodes),
        response = response?.mapToModel(context),
        asyncResponse = asyncResponse?.mapToModel(context),   // NEW
        requestBody = requestBody.mapToModel(),
        headers = headers.mapList { it.mapToModelInHeaderContext() },
        description = description,
    )
}
```

---

## State of the Art

Not applicable — this is a greenfield feature addition within an existing proprietary codebase, not a framework upgrade decision.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `FieldListElement` subtypes include at least `Field` and `FieldListIncludeElement` — and `Field` has a `name: String` property | Architecture Patterns, Pitfall 3 | `responseHasJobField` helper would need different cast/accessor | 

The A1 claim is effectively verified by the existing test files (`FieldListIncludeElementTest.kt`) which use `Field` and `FieldListIncludeElement` as the two concrete subtypes of `FieldListElement` in `storage.type`. `Field` has `name` used throughout. **Risk: LOW.**

---

## Open Questions

1. **`responseHasJobField` placement**
   - What we know: The helper is called only from `storage.Method.validate()` in this phase.
   - What's unclear: Whether it belongs as a `private fun` inside `Method.kt` or as a package-level function in the `storage` package.
   - Recommendation: Private function inside `Method.kt` — it has no other callers and is logically part of Method's validation concern. If Phase 2 or 3 needs it, it can be promoted then.

2. **warningEmitter routing in DocValidator (D-03 discretion)**
   - What we know: `getValidatedDocument` already has a `messageHandler: (String) -> Unit` parameter. `validate()` is called twice in the file via two separate mappers.
   - What's unclear: Whether warnings should go to `messageHandler` (caller-visible) or `System.err` (always visible but bypasses caller control).
   - Recommendation: Route to `messageHandler` — this preserves the existing pattern where all informational output goes through the caller-supplied callback. `System.err` would make warnings invisible to automated callers that capture stdout/stderr separately.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 1 is code-only changes with no external service dependencies. Gradle 8.12 / Kotlin 2.1.20 / JVM 17 are confirmed present in the project build configuration.

---

## Validation Architecture

`nyquist_validation` is explicitly `false` in `.planning/config.json`. This section is omitted per configuration.

---

## Security Domain

No security-relevant changes in this phase. The `asyncResponse` field is documentation metadata only; it does not affect authentication, authorization, or data exposure at runtime.

---

## Sources

### Primary (HIGH confidence — verified from live codebase)

- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt` — Interface definition; confirmed single method signature
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt` — Constructor pattern; confirmed single `validationContext` param today
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt` — Full override list; `createDelegate` method signature
- `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt` — `createContextual()` and `replaceDelegatee()` factory sites confirmed at lines 36-54
- `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt` — Existing `validate()` branching pattern; data class constructor; `Field` import already present
- `src/main/java/com/giffardtechnologies/restdocs/storage/Response.kt` — `typeRef: String?` confirmed; `fields: ArrayList<FieldListElement>?` confirmed via `TypeSpec` parent
- `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` — Plain class constructor; `response: Response? = null` at line 16
- `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` — `MethodStorageModel.mapToModel()` at line 506; `ResponseStorageModel.mapToModel()` at line 534
- `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt` — Two `createMapper()` call sites confirmed at lines 39, 52; `messageHandler` parameter confirmed
- `src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt` — `createMapper()` signature confirmed; `ValidationModule(validationContext)` construction confirmed
- `.planning/phases/01-storage-model-validation-foundation/01-CONTEXT.md` — All locked decisions D-01 through D-08
- `src/test/kotlin/com/giffardtechnologies/restdocs/storage/type/FieldListIncludeElementTest.kt` — Confirms `Field` and `FieldListIncludeElement` are the two `FieldListElement` subtypes in storage.type

### Secondary (MEDIUM confidence)

- `.claude/docbuild-async-response-spec.md` — Format spec; canonical typeref names `AsyncJobResponse` and `AsyncCapableResponse` for D-07

---

## Metadata

**Confidence breakdown:**
- Storage/domain model changes: HIGH — field addition patterns are direct and verified against existing fields
- Validatable 2-arg overload: HIGH — Kotlin default interface methods are well-established; confirmed against existing interface
- ValidationModule chain threading: HIGH — all four files read in full; factory sites identified
- Cross-field validation logic: HIGH — existing `validate()` branching pattern verified; `FieldListElement` type hierarchy verified via tests
- DocValidator wiring: HIGH — both mapper creation sites confirmed; `messageHandler` signature verified

**Research date:** 2026-06-02
**Valid until:** This research is against a private codebase at a specific commit. Valid until next commit touching these files.
