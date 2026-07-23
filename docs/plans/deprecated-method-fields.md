# Add `deprecated`/`deprecationNote`/`deprecatedSince` to `Method`

## Context

This is a follow-on to the recently-shipped `noPayload` flag on `Response` (async-no-payload work). While retroactively documenting Allego's 52 `AsyncCapableResponse`/`AsyncJobResponse` methods, several turned out to be deprecated no-op handlers kept only for backward compatibility. There is currently no structured way to mark a `Method` as deprecated in `reference.yaml` — only free-text in `description`, which the validator can't check for completeness.

This plan implements the **mechanism only**: the schema fields, validator rules, codegen filtering/annotation, and doc-generator rendering. Migrating the actual 52 methods' YAML entries to use the new fields is explicitly out of scope — separate follow-up work.

All file/line claims below were verified directly against the current codebase, not assumed.

**Design note on the codegen skip-filter**: `kotlinx.datetime.LocalDate.MIN` is `internal` in the kotlinx-datetime 0.6.1 artifact, not public, so it can't be used in a filter expression like `it.deprecatedSince ?: LocalDate.MIN`. The plan below uses an equivalent early-return check instead (see Wave E).

**Pre-existing, unrelated issue found during verification** (not part of this plan): two `rest_api_doc.vm` files exist — the root-level one (canonical, deployed to the real Allego server repo via `tools/copy-doc-template.sh`) and `docs/rest_api_doc.vm` (stale, missing async/noPayload support, referenced by `docs/docbuild.properties`). All doc-template edits below target **the root file only**.

---

## Wave A — Schema + storage layer

**`docs/reference.yaml`** — add three field defs to the `Method` object, after `failure codes` (line 461), matching the style of the existing `Response.noPayload` field (lines 474-481: `name`/`description`/`required`/`type`/`default`, no `sampleValues` for booleans, nullable strings omit `default`):

```yaml
          - name: deprecated
            description: Indicates that this method is deprecated and should not be used for new
                         integrations. When true, 'deprecationNote' is required.
            required: no
            type: boolean
            default: false
          - name: deprecationNote
            description: Human-readable explanation of the deprecation, e.g. what to use instead.
                         Required when 'deprecated' is true; must not be set otherwise.
            required: no
            type: string
          - name: deprecatedSince
            description: The ISO-8601 date ('yyyy-MM-dd') the method was deprecated. Must not be a
                         future date. Only valid when 'deprecated' is true. Strongly recommended
                         when known — an absent date is treated as "arbitrarily old" by codegen's
                         skip-cutoff filter.
            required: no
            type: string
            sampleValues:
                - 2026-08-01
```

**`src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt`** — add three constructor params after `failureCodes` (line 55):

```kotlin
    val deprecated: Boolean = false,
    val deprecationNote: String? = null,
    val deprecatedSince: String? = null,
) : Validatable {
```

Add imports: `kotlinx.datetime.LocalDate`, `kotlinx.datetime.toKotlinLocalDate`.

Extend single-arg `validate(validationContext: Any?)` (lines 62-77), after the existing id/path/duplicate-name checks:

```kotlin
        if (!deprecated) {
            if (deprecationNote != null) {
                throw ValidationException("Method '$name': 'deprecationNote' can only be set when 'deprecated' is true")
            }
            if (deprecatedSince != null) {
                throw ValidationException("Method '$name': 'deprecatedSince' can only be set when 'deprecated' is true")
            }
        } else {
            if (deprecationNote.isNullOrBlank()) {
                throw ValidationException("Method '$name': 'deprecationNote' is required when 'deprecated' is true")
            }
            if (deprecatedSince != null) {
                val parsedDeprecatedSince = try {
                    LocalDate.parse(deprecatedSince)
                } catch (e: IllegalArgumentException) {
                    throw ValidationException(
                        "Method '$name': 'deprecatedSince' must be an ISO-8601 date (yyyy-MM-dd), got: '$deprecatedSince'"
                    )
                }
                val today = java.time.LocalDate.now().toKotlinLocalDate()
                if (parsedDeprecatedSince > today) {
                    throw ValidationException(
                        "Method '$name': 'deprecatedSince' ($deprecatedSince) cannot be a future date"
                    )
                }
            }
        }
```

Extend the two-arg `validate(validationContext, warningEmitter)` (lines 79-93), inside the existing `if (validationContext !is DocValidator.AccumulatingContext)` block, alongside VALID-03/VALID-04 — a *warning*, not a throw, since a date genuinely isn't always known for retroactively-documented methods:

```kotlin
            // deprecated but no deprecatedSince recorded
            if (deprecated && deprecatedSince == null) {
                warningEmitter("WARNING: Method '$name': deprecated but has no 'deprecatedSince' date")
            }
```

This must live in the two-arg override (not the single-arg one) to avoid double-emitting across DocValidator's two validation passes, matching how VALID-03 already does it.

---

## Wave B — Domain model + mapping (depends on Wave A)

**`src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt`** — append to constructor (after `description`, line 20):

```kotlin
    val deprecated: Boolean = false,
    val deprecationNote: String? = null,
    val deprecatedSince: LocalDate? = null,
```

Add `import kotlinx.datetime.LocalDate`. Every existing call site builds `Method(...)` with named arguments (`MethodProcessorAsyncResponseTest.kt`, `MethodProcessorTypeRefTest.kt`, `DataObjectProcessorTest.kt`, `DataObjectUsageClassifierTest.kt`, `StorageToDomainMappers.kt`) — confirmed via grep — so appending defaulted trailing params is non-breaking.

**`src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`** — in `MethodStorageModel.mapToModel()` (lines 512-530), add:

```kotlin
        deprecated = deprecated,
        deprecationNote = deprecationNote,
        deprecatedSince = deprecatedSince?.let { LocalDate.parse(it) },
```

Add `import kotlinx.datetime.LocalDate`. This parse is safe unwrapped — `mapToModel()` only ever runs on an already-validated `storage.Document`, and the validator (Wave A) guarantees the date parses.

---

## Wave C — Codegen annotation (depends on Wave B)

**`src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt`** — in `processMethod()` (line 212), right after `requestClassBuilder` is built (after line 225's `.addModifiers(KModifier.PUBLIC)`, before the `parameters.isEmpty` branch):

```kotlin
        if (method.deprecated) {
            requestClassBuilder.addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember("message = %S", method.deprecationNote ?: "")
                    .build()
            )
        }
```

Add `import com.squareup.kotlinpoet.AnnotationSpec` (not currently imported in this file). `requestClassBuilder` is a KotlinPoet `Builder` that mutates in place (already called imperatively at line 228 elsewhere in this function), so this is a plain statement, no reassignment needed.

---

## Wave D — Doc generation (depends on Wave A; storage-model direct, no domain-model dependency)

**`src/main/java/com/giffardtechnologies/restdocs/htmlgen/ObjectInspectionHelper.kt`** — add, matching the existing block-body style (e.g. `hasNoPayload`, lines 135-137):

```kotlin
    fun isDeprecated(method: Method): Boolean {
        return method.deprecated
    }

    fun hasDeprecationNote(method: Method): Boolean {
        return !method.deprecationNote.isNullOrBlank()
    }

    fun hasDeprecatedSince(method: Method): Boolean {
        return !method.deprecatedSince.isNullOrBlank()
    }
```

**`rest_api_doc.vm`** (root file only) — method header (line 363) and description paragraph (line 364):

```
<h4 id="$method.name">$method.name#if($helper.isDeprecated($method)) <span style="background-color:#c0392b;color:#fff;padding:2px 6px;border-radius:3px;font-size:0.7em;margin-left:6px;">DEPRECATED</span>#end<span style="font-size: 0.6em; font-weight: normal; margin-bottom: .8em; margin-left: .4em;">(id: $method.id)</span></h4>
<p>$helper.getDescription($method)</p>
#if ($helper.isDeprecated($method))
    <p><strong>Deprecated</strong>#if($helper.hasDeprecatedSince($method)) since $method.deprecatedSince#end#if($helper.hasDeprecationNote($method)): $method.deprecationNote#end</p>
#end
```

Use an inline `style` attribute (not a new CSS class) — `tools/copy-doc-template.sh` only copies the `.vm` file to the deployed server repo, not `css/api.css`, so a new class name would require an out-of-band edit to a file this repo doesn't own. No existing colored-badge convention exists in this template today; this establishes one.

Do not touch the TOC/sidebar `#foreach( $resource in $document.service.methods )` blocks (appear twice, ~lines 177 and 234) — deferred, see Non-goals.

---

## Wave E — Generator refactor + skip-filter (depends on Wave B)

Do this as **one atomic change**, not staged with a temporary overload: there are only two call sites for `KotlinGenerator.generate()` in the whole repo (`KotlinGeneratorCommand.kt:86` and `AsyncResponseSmokeTest.kt:40` — confirmed via grep), both need editing either way, and this is an internal single-repo tool with no external consumer needing a deprecation window.

**`src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt`** — change `fun generate(sourceFile: File, options: Options)` to `fun generate(document: Document, options: Options)`, removing the internal `DocValidator().getValidatedDocument(sourceFile).mapToModel()` call (validation/mapping moves to the caller). As the very first statement in `generate()`, shadow the parameter with the filtered document:

```kotlin
fun generate(document: Document, options: Options) {
    val document = document.filterDeprecatedMethods(options.skipDeprecatedBefore, options.verboseLogging)
    ...
}
```

Add `skipDeprecatedBefore: LocalDate? = null` to `Options`. Add the filter as private members of `KotlinGenerator` itself (Document/Service have no `copy()`, so reconstruct explicitly using their actual constructors — `Document(title, bitsets, enumerations, dataObjects, service)` and `Service(description, basePath, common, methods)`, both verified):

```kotlin
private fun Document.filterDeprecatedMethods(skipDeprecatedBefore: LocalDate?, verboseLogging: Boolean): Document {
    val service = this.service ?: return this
    if (skipDeprecatedBefore == null) return this
    if (verboseLogging) {
        service.methods.filter { it.shouldSkipForCodegen(skipDeprecatedBefore) }.forEach { method ->
            println("Skipping deprecated method '${method.name}' (deprecatedSince=${method.deprecatedSince ?: "unknown"})")
        }
    }
    val kept = service.methods.filter { !it.shouldSkipForCodegen(skipDeprecatedBefore) }
    return Document(
        title, bitsets, enumerations, dataObjects,
        Service(service.description, service.basePath, service.common, kept)
    )
}

private fun Method.shouldSkipForCodegen(skipDeprecatedBefore: LocalDate): Boolean {
    if (!deprecated) return false
    val since = deprecatedSince ?: return true // unknown deprecatedSince => always skip-eligible
    return since < skipDeprecatedBefore
}
```

Add imports to `KotlinGenerator.kt`: `kotlinx.datetime.LocalDate`, `com.giffardtechnologies.restdocs.domain.Method`, `com.giffardtechnologies.restdocs.domain.Service` (`Document` was already imported for the signature change).

**`src/main/java/com/giffardtechnologies/restdocs/KotlinGeneratorCommand.kt`** — owns only validation, mapping, and reading the `skipDeprecatedBefore.date` property; it no longer applies the filter itself, just passes the parsed date through `Options`:

```kotlin
        val skipDeprecatedBefore: LocalDate? = properties.getProperty("skipDeprecatedBefore.date")?.let { value ->
            try {
                LocalDate.parse(value.trim())
            } catch (e: IllegalArgumentException) {
                error("skipDeprecatedBefore.date must be an ISO-8601 date (yyyy-MM-dd), got: '$value'")
            }
        }

        val document = DocValidator().getValidatedDocument(sourceFile).mapToModel()

        KotlinGenerator().generate(
            document,
            Options(codeDir, iOSCodeDir, clientPackage, false, forceTopLevel, excludedFields, asyncJobStatusConfig, skipDeprecatedBefore)
        )
```

`verboseLogging = false` stays hardcoded — wiring it to a properties key is out of scope (it's already dead code today).

**Why filter as the first step inside `generate()` rather than in the caller**: within `generate()`, the `document` value is read independently by three consumers — the `asyncJobStatusConfig` methodID lookup, the `SwiftAPIServerClient` loop, and `DataObjectUsageClassifier`. Filtering once, before any of them run, keeps all three consistent without three separate fixes — the shadowed `val document = document.filterDeprecatedMethods(...)` at the top of the function guarantees every line below it, including all three consumers, only ever sees the filtered list. If the configured `asyncJobStatus.methodID` itself got filtered out, the existing `?: error(...)` fallback for that lookup fires with a clear message instead of a silent dangling reference. Doing this inside `generate()` (rather than in `KotlinGeneratorCommand`) also means any other future caller of `generate()` gets the same guarantee for free, instead of having to remember to filter before calling.

**`src/test/kotlin/com/giffardtechnologies/restdocs/codegen/AsyncResponseSmokeTest.kt`** — line 40 currently calls `KotlinGenerator().generate(sourceFile, options)` and will not compile after the signature change. Update to validate+map first:

```kotlin
val document = DocValidator().getValidatedDocument(sourceFile).mapToModel()
KotlinGenerator().generate(document, options)
```

---

## Non-goals (explicitly deferred)

- No `Field`/`DataObject`-level `deprecated` — `Method` only.
- No change to how a deprecated method's other fields (`parameters`/`response`/`asyncResponse`) validate.
- No implicit default for `skipDeprecatedBefore` — explicit `kotlin-gen.properties` key only, `null` preserves today's behavior.
- No skip option for `doc_generator` — deprecated methods always appear in generated docs.
- No deprecation marker in the TOC/sidebar method list.
- No wiring of `verboseLogging` to a properties key (stays dead code, as today).
- No migration of the actual 52 `reference.yaml` method entries (confirmed out of scope).

---

## New test coverage

1. **New file `src/test/kotlin/.../storage/MethodValidationTest.kt`** — direct unit tests of `Method(...).validate(ctx)` / the two-arg overload, bypassing Jackson: deprecated=true+no note → throws; blank note → throws; deprecated=false+note set → throws; deprecated=false+deprecatedSince set → throws; malformed date → throws; future date → throws; valid past date → passes; deprecated=true+no deprecatedSince → passes but warningEmitter receives a warning.
2. **New YAML fixture** (e.g. `src/test/resources/deprecated-method-test.yaml`, modeled on the existing `async-response-smoke-test.yaml`) with methods covering: deprecated+valid past date, deprecated+no date, non-deprecated (regression).
3. **New file `MethodProcessorDeprecatedTest.kt`** (mirroring `MethodProcessorAsyncResponseTest.kt`) — asserts `processMethod` emits `@Deprecated(message = "...")` when `method.deprecated`, and omits it otherwise.
4. **Skip-filter coverage** — a small integration test (new `KotlinGeneratorCommandTest.kt`, or extend `AsyncResponseSmokeTest.kt`) building a `Document` with a mix of deprecated/non-deprecated/unknown-date methods and a `skipDeprecatedBefore` cutoff, verifying which methods are excluded from generated output.
5. **Doc template** — extend `DocGeneratorAsyncHtmlTest.kt`'s fixture with a deprecated method, asserting the badge/note render only for that method's section (reuse its existing `.substringAfter("id=\"...\"")` pattern).

## Verification

- Run `./gradlew test` after Wave E (the only wave with a guaranteed compile break if `AsyncResponseSmokeTest.kt` isn't updated alongside it).
- Regression-check without code changes needed: `DocGeneratorAsyncHtmlTest.kt`, `MethodProcessorAsyncResponseTest.kt`, `MethodProcessorTypeRefTest.kt`, `DataObjectProcessorTest.kt`, `DataObjectUsageClassifierTest.kt` — all construct `Method(...)` with named args/defaults, so the new fields should be inert for them.
- Manually run `doc_generator` against a small YAML fixture containing one deprecated method and inspect the rendered HTML for the badge + note.
- Manually run `kotlin_generator` with and without `skipDeprecatedBefore.date` set in `kotlin-gen.properties` to confirm the filter both no-ops (property absent) and excludes methods (property set) as expected.
