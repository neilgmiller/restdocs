# Phase 1: Storage Model + Validation Foundation - Pattern Map

**Mapped:** 2026-06-02
**Files analyzed:** 8
**Analogs found:** 8 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt` | model | CRUD | `src/main/java/com/giffardtechnologies/restdocs/storage/Response.kt` (field pattern) + self (validate pattern) | exact |
| `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` | model | CRUD | `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` (self — `response` field is the direct analog) | exact |
| `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` | transform | transform | `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` lines 517 (`response` mapping is direct analog) | exact |
| `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt` | middleware | request-response | self — extending the single-method interface | exact |
| `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt` | config | request-response | self — existing `validationContext` constructor param is the analog | exact |
| `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt` | middleware | request-response | self — `createDelegate` method is the threading analog | exact |
| `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt` | middleware | request-response | self — `createContextual` / `replaceDelegatee` / `deserialize` are the three threading sites | exact |
| `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt` | controller | request-response | self — two `createMapper()` call sites at lines 38 and 52 | exact |

---

## Pattern Assignments

### `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt` (model, CRUD)

**Analog for field addition:** `response: Response? = null` at line 50 of the same file — `asyncResponse` follows the identical pattern.

**Existing data class constructor** (lines 36–55):
```kotlin
data class Method(
    val method: HTTPMethod? = null,
    val path: String? = null,
    @JsonProperty("protocols allowed")
    val protocolsAllowed: ArrayList<String> = ArrayList(),
    val id: Int? = null,
    val name: String = "",
    val description: String? = "",
    @JsonProperty("authentication required")
    val isAuthenticationRequired: Boolean = true,
    val headers: ArrayList<Field>? = ArrayList(),
    val parameters: ArrayList<FieldListElement>? = null,
    @JsonProperty("request body")
    val requestBody: RequestBody? = null,
    val response: Response? = null,            // <-- add asyncResponse immediately after this
    @JsonProperty("successful codes")
    val successCodes: ArrayList<String> = ArrayList(),
    @JsonProperty("failure codes")
    val failureCodes: ArrayList<String> = ArrayList(),
) : Validatable {
```

**No `@JsonProperty` annotation rule:** Fields like `response`, `id`, `name` carry no annotation because the YAML key already matches the property name. Fields with YAML keys that differ (spaces, camelCase mismatches) use `@JsonProperty`. `asyncResponse` matches the YAML key exactly — no annotation.

**Existing validate() override — the branching pattern to extend** (lines 61–76):
```kotlin
override fun validate(validationContext: Any?) {
    if (path == null && id == null) {
        throw ValidationException("A method must have at least one of 'id' and 'path'")
    }
    if (validationContext is DocValidator.AccumulatingContext) {
        if (validationContext.methodClassNames.contains(name)) {
            throw ValidationException("A method already exists with the name: \"$name\"")
        } else {
            parameters?.validateHasNoDuplicates()
            validationContext.methodClassNames.add(name)
        }
    } else {
        parameters?.validateHasNoDuplicates(validationContext.documentIfAvailable)
    }
}
```

**New 2-arg override to add** — follows the same `AccumulatingContext` gate; calls 1-arg form first then adds cross-field checks in the `else` branch:
```kotlin
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

private fun responseHasJobField(response: Response?): Boolean {
    if (response == null) return false
    if (response.typeRef in setOf("AsyncJobResponse", "AsyncCapableResponse")) return true
    return response.fields?.any { it is Field && it.name == "job" } == true
}
```

**Import already present for `Field`** (line 8): `import com.giffardtechnologies.restdocs.storage.type.Field` — no new import needed for the `is Field` check.

---

### `src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt` (model, CRUD)

**Analog:** `var response: Response? = null` at line 16 of the same file — `asyncResponse` follows the identical pattern.

**Existing plain class constructor** (lines 6–20):
```kotlin
class Method(
    val method: HTTPMethod,
    val path: String? = null,
    val protocolsAllowed: Array<String> = Array.of("HTTP"),
    val id: Int? = null,
    val name: String,
    val isAuthenticationRequired: Boolean,
    val parameterElementList: FieldElementList,
    var failureCodes: Array<String> = Array.empty(),
    var successCodes: Array<String> = Array.empty(),
    var response: Response? = null,            // <-- add asyncResponse immediately after this
    var requestBody: RequestBody? = null,
    val headers: Array<Field> = Array.empty(),
    val description: String? = null
) {
```

**Key note:** This is NOT a data class. Use `var asyncResponse: Response? = null` with a default of `null` so existing call sites require no changes.

---

### `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` (transform, transform)

**Analog:** `response = response?.mapToModel(context)` at line 517 — `asyncResponse` is a one-line addition using the identical safe-call pattern.

**Existing `MethodStorageModel.mapToModel` body** (lines 506–523):
```kotlin
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
        response = response?.mapToModel(context),    // <-- asyncResponse = asyncResponse?.mapToModel(context) goes after this
        requestBody = requestBody.mapToModel(),
        headers = headers.mapList { it.mapToModelInHeaderContext() },
        description = description,
    )
}
```

**The `ResponseStorageModel.mapToModel` being reused** (lines 534–539):
```kotlin
private fun ResponseStorageModel.mapToModel(context: Context): Response {
    return Response(
        typeSpec = this.mapToModel("response", context),
        description = description,
    )
}
```
This function is applied to `asyncResponse` verbatim — no change needed, VALID-02 is satisfied for free.

---

### `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt` (middleware, request-response)

**Analog:** The existing `fun validate(validationContext: Any?)` is the 1-arg form that the new 2-arg overload must delegate to.

**Current interface** (lines 1–7):
```kotlin
interface Validatable {
    fun validate(validationContext: Any?)
}

class ValidationException(message: String) : Exception(message)
```

**New 2-arg overload to add** — Kotlin default interface method; existing implementations inherit it and require zero changes:
```kotlin
interface Validatable {
    fun validate(validationContext: Any?)

    fun validate(validationContext: Any?, warningEmitter: (String) -> Unit) {
        validate(validationContext)
    }
}
```

---

### `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt` (config, request-response)

**Analog:** The existing `validationContext: Any? = null` constructor param — `warningEmitter` follows the identical defaulted-parameter pattern.

**Current file** (lines 1–9):
```kotlin
class ValidationModule(validationContext: Any? = null) : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier(validationContext))
    }
}
```

**After change** — add `warningEmitter` as second defaulted constructor param and thread it to the modifier:
```kotlin
class ValidationModule(
    validationContext: Any? = null,
    warningEmitter: (String) -> Unit = {},
) : SimpleModule() {
    init {
        setDeserializerModifier(ValidatingBeanDeserializerModifier(validationContext, warningEmitter))
    }
}
```

---

### `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt` (middleware, request-response)

**Analog:** The existing `private val validationContext: Any?` primary constructor property — `warningEmitter` is threaded identically.

**Current constructor and createDelegate** (lines 8, 97–99):
```kotlin
class ValidatingBeanDeserializerModifier(private val validationContext: Any?) : ValueDeserializerModifier() {
    // ...
    private fun createDelegate(type: JavaType, target: ValueDeserializer<*>): ValueDeserializer<*> {
        return ValidatingDeserializer(target, validationContext)
    }
}
```

**After change** — add `warningEmitter` as second constructor property and pass it through `createDelegate`:
```kotlin
class ValidatingBeanDeserializerModifier(
    private val validationContext: Any?,
    private val warningEmitter: (String) -> Unit,
) : ValueDeserializerModifier() {
    // ... all modifyXxx overrides unchanged ...
    private fun createDelegate(type: JavaType, target: ValueDeserializer<*>): ValueDeserializer<*> {
        return ValidatingDeserializer(target, validationContext, warningEmitter)
    }
}
```

**Note on `modifyKeyDeserializer`** (lines 80–95): This override directly calls `deserializedKey.validate(validationContext)` rather than going through `createDelegate`. It must also be updated to call the 2-arg form: `deserializedKey.validate(validationContext, warningEmitter)`.

---

### `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt` (middleware, request-response)

**Three constructor sites — all must receive `warningEmitter`.** The existing `validationContext` threading is the direct analog.

**Current class header and three factory sites** (lines 19, 32–54, 62–74):
```kotlin
// Primary constructor (line 19):
class ValidatingDeserializer(
    private val _delegatee: ValueDeserializer<*>,
    private val validationContext: Any?
) : StdDeserializer<Any?>(...)

// createContextual (lines 32–46) — new instance built at line 44:
return if (del === _delegatee) {
    this
} else {
    ValidatingDeserializer(del, validationContext)   // <-- add warningEmitter
}

// replaceDelegatee (lines 48–54) — new instance at line 52:
return if (delegatee === _delegatee) {
    this
} else {
    ValidatingDeserializer(delegatee, validationContext)   // <-- add warningEmitter
}

// deserialize (lines 62–74) — validate call site at line 69:
deserializedObject.validate(validationContext)   // <-- change to 2-arg form
```

**After change** — all four sites updated:
```kotlin
class ValidatingDeserializer(
    private val _delegatee: ValueDeserializer<*>,
    private val validationContext: Any?,
    private val warningEmitter: (String) -> Unit,
) : StdDeserializer<Any?>(_delegatee.handledType()) {

    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val vt = ctxt.constructType(_delegatee.handledType())
        val del = ctxt.handleSecondaryContextualization(_delegatee, property, vt)
        return if (del === _delegatee) this
        else ValidatingDeserializer(del, validationContext, warningEmitter)
    }

    override fun replaceDelegatee(delegatee: ValueDeserializer<*>): ValueDeserializer<*> {
        return if (delegatee === _delegatee) this
        else ValidatingDeserializer(delegatee, validationContext, warningEmitter)
    }

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
    // ... all other overrides unchanged ...
}
```

---

### `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt` (controller, request-response)

**Analog:** The existing `createMapper(AccumulatingContext(validationOptions))` pattern at lines 38 and 52 — both calls receive the same additional `warningEmitter` argument.

**Current `getValidatedDocument`** (lines 34–61):
```kotlin
fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}): Document {
    println("Validating '${sourceFile.absolutePath}'...")

    val mapper = createMapper(AccumulatingContext(validationOptions))          // line 38
    val document = mapper.readValue(
        BufferedInputStream(FileInputStream(sourceFile)),
        DocumentStorageModel::class.java
    )

    messageHandler("First pass complete")

    val dataObjectNames = document.dataObjects.map { it.name }
    // ... type set building ...
    val referencableTypes = VavrHashSet.ofAll(...)

    val contextMapper = createMapper(FullContext(referencableTypes, document, validationOptions))  // line 52
    contextMapper.readValue(
        BufferedInputStream(FileInputStream(sourceFile)),
        DocumentStorageModel::class.java
    )
    messageHandler("Second pass complete")
    messageHandler("SUCCESS!")

    return document
}
```

**After change** — define `warningEmitter` once from `messageHandler`, pass to both `createMapper` calls:
```kotlin
fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}): Document {
    println("Validating '${sourceFile.absolutePath}'...")

    val warningEmitter: (String) -> Unit = { message -> messageHandler(message) }

    val mapper = createMapper(AccumulatingContext(validationOptions), warningEmitter)
    // ... unchanged ...
    val contextMapper = createMapper(FullContext(referencableTypes, document, validationOptions), warningEmitter)
    // ... unchanged ...
}
```

---

### `src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt` (config, request-response)

**Analog:** The existing `validationContext: Any? = null` defaulted parameter — `warningEmitter` follows the identical pattern.

**Current `createMapper`** (lines 12–21):
```kotlin
fun createMapper(validationContext: Any? = null): YAMLMapper {
    val booleanModule = SimpleModule().addDeserializer(Boolean::class.java, YesNoBooleanDeserializer())
    return YAMLMapper.builder()
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .addModule(kotlinModule())
        .addModule(booleanModule)
        .addModule(ValidationModule(validationContext))
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()
}
```

**After change** — add `warningEmitter` with default no-op; preserves all existing callers (`DocGenerator`, `KotlinGenerator`) that don't supply a warning emitter:
```kotlin
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

---

## Shared Patterns

### Defaulted Lambda Constructor Parameter
**Source:** `JacksonMapperBuilder.kt` line 12 — `validationContext: Any? = null`
**Apply to:** `ValidationModule`, `ValidatingBeanDeserializerModifier`, `ValidatingDeserializer`, `createMapper`

The project convention for injecting an optional dependency through a construction chain is a defaulted parameter (`= null` for objects, `= {}` for function types). This preserves backward compatibility for all existing callers.

### AccumulatingContext Gate
**Source:** `storage/Method.kt` lines 65–74
**Apply to:** `storage.Method.validate()` 2-arg override
```kotlin
if (validationContext is DocValidator.AccumulatingContext) {
    // first-pass only checks
} else {
    // full-context checks (type resolution available)
}
```
VALID-03 and VALID-04 belong in the `else` branch — same placement as the existing `validateHasNoDuplicates(validationContext.documentIfAvailable)` call.

### ValidationException throw pattern
**Source:** `storage/Method.kt` lines 63, 67
**Apply to:** VALID-04 in `storage.Method.validate()`
```kotlin
throw ValidationException("A method must have at least one of 'id' and 'path'")
```
Single-string constructor, thrown directly from within `validate()`.

### Safe-call mapToModel pattern
**Source:** `StorageToDomainMappers.kt` line 517
**Apply to:** `asyncResponse` mapping in `MethodStorageModel.mapToModel`
```kotlin
response = response?.mapToModel(context),
```
The `?.` safe call means `null` input produces `null` output without needing a null guard.

---

## No Analog Found

None. All eight files have direct analogs in the codebase — in most cases the file is modifying itself by mirroring an already-existing field or parameter.

---

## Metadata

**Analog search scope:** `src/main/java/com/giffardtechnologies/restdocs/`
**Files scanned:** 9 source files read in full
**Pattern extraction date:** 2026-06-02
