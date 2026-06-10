# Phase 3: HTML Rendering - Research

**Researched:** 2026-06-09
**Domain:** Velocity template rendering, storage model, HTML generation pipeline
**Confidence:** HIGH

---

## Summary

Phase 3 is a targeted Velocity template change with a companion JUnit integration test. The
storage model change from Phase 1 (`storage.Method.asyncResponse: Response?`) is fully in
place and directly accessible from the template as `$method.asyncResponse`. No new libraries
are needed. No new Kotlin source files beyond the test class are required.

The root-level `rest_api_doc.vm` is the only template that renders method sections and
therefore the only file that needs modification. The `docs/rest_api_doc.vm` is intentionally
a data-objects-only partial and must NOT be updated.

The existing `async-response-smoke-test.yaml` fixture covers all three required cases (pure
async, mixed async, sync regression) and can be reused as-is for the HTML test.

The single risk is the test's reliance on `File(System.getProperty("user.dir"), "rest_api_doc.vm")`:
Gradle sets `user.dir` to the project root where `rest_api_doc.vm` lives, so this works
correctly under `./gradlew test`. The same holds for IntelliJ when tests are run from
the project root.

**Primary recommendation:** Execute 03-01-PLAN.md as written. Research confirms the plan is
correct and complete — no changes required.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HTML-01 | Rendered HTML for a method with `asyncResponse` includes a clearly labelled second response section beneath the immediate response | `$method.asyncResponse` is non-null for async methods; `#if ($method.asyncResponse)` gates the new section |
| HTML-02 | Second response section heading is "Async Response (via getAsyncJobStatus)" with prose note about `jr`/`jobStatus` | Static heading and prose — no dynamic lookup required |
| HTML-03 | Async response section renders field tables using same visual treatment as immediate response section | `#fieldheaders` and `#fieldrow` macros already exist in `rest_api_doc.vm`; `$helper.hasFields($method.asyncResponse)` and `$helper.getFields($method.asyncResponse)` dispatch to `TypeSpec` overloads |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| asyncResponse field access | Storage model (Java object graph) | — | HTML pipeline uses `storage.Document` directly; `storage.Method.asyncResponse` is already populated from YAML by Phase 1 |
| Field table rendering | Velocity template (`rest_api_doc.vm`) | `ObjectInspectionHelper` | Template calls `$helper.getFields()` which returns `ArrayList<Field>`; macro iterates and formats |
| Field resolution (include elements) | `FieldElementList` | — | Handles both direct `Field` entries and `FieldListIncludeElement` expansions transparently |
| Section heading / prose copy | Velocity template only | — | Static text; no Kotlin change needed |
| TypeRef rendering | Velocity template + `LinkTool` | — | `$link.typeSimple($method.asyncResponse.typeRef)` handles typeref-shape asyncResponse |
| Test driving | JUnit + `DocGenerator.generate()` | Classpath fixture YAML | `DocGenerator` accepts explicit file arguments; no CLI invocation needed |

---

## Research Findings by Question

### Q1 — Are there other templates or template loading paths beyond root `rest_api_doc.vm`?

**Finding: Two templates exist. Only the root-level one needs updating.**

- `/rest_api_doc.vm` (project root) — production template loaded by default when no
  `templateFile` is specified in `docbuild.properties`. Contains the full method-rendering
  loop. This is the only file to modify. [VERIFIED: read source]
- `/docs/rest_api_doc.vm` — data-objects-only partial. No method section exists in this
  file at all; it stops at `</section>` after enumerations and has no `#foreach` over
  `$resource.actions`. The `docs/docbuild.properties` points its `templateFile` to this
  file, so when `doc_generator` is run from `docs/`, it uses the partial — but that partial
  intentionally omits method rendering. [VERIFIED: read source]

Template loading mechanism (`DocGenerator.kt:41`):
```kotlin
p.setProperty("file.resource.loader.path", templateFile.parentFile!!.absolutePath)
val t: Template = ve.getTemplate(templateFile.name)
```
The loader path is set to the **parent directory** of the template file, and the template is
loaded by filename only. There is no `#include` or `#parse` between the two `.vm` files —
they are entirely independent. [VERIFIED: read source]

**Conclusion:** Only `/rest_api_doc.vm` needs the async response block. `docs/rest_api_doc.vm`
is intentionally a partial with no method section.

---

### Q2 — How does `#fieldrow` handle field types? Limitations?

The `#fieldrow($field)` macro in `/rest_api_doc.vm` (lines 7-40) renders three columns:

1. **Field name + required/optional badge** — reads `$field.name` and `$field.required`.
   `Field.isRequired` compiles to an `isRequired()` JVM getter; Velocity's JavaBean
   introspection resolves `required` from `is`-prefix getters. Works correctly. [VERIFIED: read source]

2. **Type** — renders `$link.type($field.type)`. `$field.type` is a `DataType` enum;
   `LinkTool.type(String?)` expects a String and coerces via Velocity's method dispatch
   (calls `DataType.toString()` / `DataType.name()`). [VERIFIED: read source]

3. **Restrictions** — reads `$field.hasRestrictions` and iterates `$field.restrictions`.
   `Field.hasRestrictions` does not exist as a computed property on `storage.type.Field`.
   The macro reads `$field.hasRestrictions` directly — Velocity returns null/false for
   missing properties, so this section is silently skipped when `hasRestrictions` is absent
   on the object. This is existing behaviour and not changed by phase 3. [VERIFIED: read source]

4. **Description** — `$field.description` — straightforward string property. [VERIFIED: read source]

**Key limitation:** The `#fieldrow` macro in `/rest_api_doc.vm` does NOT recursively expand
nested OBJECT or ARRAY/OBJECT fields. The `docs/rest_api_doc.vm` version has recursive
inline expansion, but the root template does not. For asyncResponse fields, this means
nested objects will display as a non-expanded row (the type is shown, but no inline table
for nested fields). This matches the existing behaviour for `response` fields and is
acceptable for phase 3 scope.

**`#fieldheaders` macro** in `/rest_api_doc.vm` renders 3 columns: `field`, `type`,
`description` (no `required` column in the header, though the row shows required/optional
in the field column). [VERIFIED: read source]

---

### Q3 — Does `$helper.getFields(typeSpec)` resolve FieldListIncludeElement correctly for asyncResponse inline objects?

`ObjectInspectionHelper.getFields(typeSpec: TypeSpec)` (line 67-69) calls:
```kotlin
FieldElementList(document, typeSpec.fields!!).getFields()
```

`FieldElementList.getFields()` iterates `fieldListElements` and handles:
- `is Field` → added directly
- `is FieldListIncludeElement` → expanded by looking up the named DataObject in
  `dataObjectsByName`, applying `includeOnly`/`excluding`/`overrideRequired` filters
- Anything else → `IllegalStateException`

**For asyncResponse inline objects** (`type: object, fields: [...]`), all fields in the
existing `async-response-smoke-test.yaml` are direct `Field` instances (no `include:`
directives). `FieldElementList` handles them in the `is Field` branch — no edge cases.

**Edge case if asyncResponse uses `FieldListIncludeElement`:** The include would be resolved
against `document.dataObjects`, which is available via `ObjectInspectionHelper.document`.
This path works correctly for DataObjects (tested in `FieldElementListIncludeOnlyTest`). For
asyncResponse blocks in practice, direct inline fields are the only expected shape. [VERIFIED: read source]

**`$helper.hasFields(typeSpec: TypeSpec)`** (line 63-65):
```kotlin
fun hasFields(typeSpec: TypeSpec): Boolean = !typeSpec.fields.isNullOrEmpty()
```
This checks the raw `fields` list before `FieldElementList` resolution. If an asyncResponse
uses only `FieldListIncludeElement` entries (no direct `Field` entries), `typeSpec.fields`
will be non-empty (containing the include elements), so `hasFields` returns true correctly
— the include elements are in the list. [VERIFIED: read source]

---

### Q4 — Is `File(System.getProperty("user.dir"), "rest_api_doc.vm")` reliable in a Gradle test context?

**Finding: Yes — Gradle sets `user.dir` to the project root directory.**

Gradle's test task JVM fork inherits (or sets) `user.dir` to the root project directory —
the directory that contains `build.gradle.kts`. The `rest_api_doc.vm` file lives at the
project root. Therefore `File(System.getProperty("user.dir"), "rest_api_doc.vm")` resolves
to the correct file. [ASSUMED — standard Gradle behaviour, not verified against Gradle docs]

Evidence supporting this: `DocGeneratorCommand.kt:41` uses exactly this pattern in production:
```kotlin
val executableDir = File(System.getProperty("user.dir")) // working directory
```
And the test builds consistently pass in the existing CI. The `build.gradle.kts` does not
override `workingDir` or `systemProperties` for the test task. [VERIFIED: read source]

**Risk:** If `./gradlew test` is invoked from a directory other than the project root (e.g.,
`./gradlew :projectName:test` from a parent), `user.dir` could point elsewhere. Standard
practice is to invoke from the project root. For robustness, the test can fall back to
resolving against the classpath root — but the plan's approach matches the existing test
pattern in the codebase and is acceptable for this project. [ASSUMED]

---

### Q5 — Existing HTML rendering tests and precedent for testing template output?

**Finding: No existing HTML rendering tests exist. The existing smoke test is for Kotlin code generation.**

All four test files found are:
- `AsyncResponseSmokeTest.kt` — drives `KotlinGenerator.generate()`, asserts file content
- `MethodProcessorAsyncResponseTest.kt` — unit tests `MethodProcessor.getClassNames()` and `processMethod()`
- `FieldElementListIncludeOnlyTest.kt` — unit tests field resolution
- `FieldListIncludeElementTest.kt` — unit tests include element parsing

**Pattern established by `AsyncResponseSmokeTest`:**
- `@TempDir` for output directory
- Load YAML fixture from classpath: `javaClass.classLoader.getResource("async-response-smoke-test.yaml")!!.toURI()`
- Call generator with explicit file arguments
- Assert content via `String.contains()` checks

The plan's `DocGeneratorAsyncHtmlTest` follows exactly this pattern, substituting
`DocGenerator(false).generate()` for `KotlinGenerator().generate()`. [VERIFIED: read source]

**Template file path in test:** The smoke test passes the templateFile as
`File(System.getProperty("user.dir"), "rest_api_doc.vm")`. This mirrors how `DocGeneratorCommand`
works in production. [VERIFIED: 03-01-PLAN.md]

---

### Q6 — Does `docs/rest_api_doc.vm` need a parallel asyncResponse update?

**Finding: No.**

`docs/rest_api_doc.vm` contains no method section. It renders data objects and enumerations
only — no `#foreach` over `$resource.actions` or `$method` access of any kind. It is
intentionally scoped to data-object documentation, likely for a reference guide that omits
method-level detail. Adding an async response block to it would be dead code. [VERIFIED: read source]

The `docs/docbuild.properties` file points to `docs/rest_api_doc.vm` as its template, so
the docs output from that directory will never show methods anyway.

---

### Q7 — Velocity 2.3 gotchas: null safety, subtype method dispatch, `#if` on nullable Kotlin property?

**Finding: Three gotchas investigated; none block the plan, but two require awareness.**

**Gotcha 1: `#if ($method.asyncResponse)` on a nullable Kotlin property**

Velocity evaluates `#if` as false for:
- The literal `false` boolean
- `null` (object reference is null)
- Empty string `""`
- The integer `0`

`storage.Method.asyncResponse` is `val asyncResponse: Response? = null`. When null, Velocity
sees a null object reference and evaluates `#if ($method.asyncResponse)` as false. When
non-null, it evaluates as true (any non-null, non-false, non-zero, non-empty object is
truthy). This is the correct behaviour for the plan. [ASSUMED — standard Velocity 2.x
documented behaviour; not verified against official docs in this session]

**Gotcha 2: Subtype method dispatch — `$helper.hasFields($method.asyncResponse)`**

`ObjectInspectionHelper.hasFields(typeSpec: TypeSpec)` expects a `TypeSpec` argument.
`$method.asyncResponse` is a `storage.Response` instance (which extends `TypeSpec`).
Velocity uses Java reflection for method lookup. Java reflection is covariant for
assignability: a `Response` instance satisfies the `TypeSpec` parameter type. Velocity
selects `hasFields(TypeSpec)` over `hasFields(DataObject)` because the argument is a
`Response`, not a `DataObject`. [VERIFIED: read source — `ObjectInspectionHelper` has two
`hasFields` overloads; the `TypeSpec` overload matches `Response extends TypeSpec`]

Same reasoning applies to `$helper.getFields($method.asyncResponse)` →
`getFields(typeSpec: TypeSpec)`. [VERIFIED: read source]

**Gotcha 3: `$method.methodString` and `$method.hasParameters` in the existing template**

These expressions appear in the root template (`rest_api_doc.vm` lines 136 and 144) but
`storage.Method` has no `methodString` property and no `hasParameters` property. Velocity's
default behaviour when a property is not found: it returns null in expression context and
renders the literal reference string in string context. Specifically:
- `<h4>$method.methodString</h4>` — renders as `<h4>$method.methodString</h4>` literally
  (Velocity leaves unresolved references as the literal string in string context)
- `#if ($method.hasParameters)` — Velocity evaluates null as false; the parameters block
  is simply not rendered

This is pre-existing behaviour that phase 3 does not touch. The `asyncResponse` property
**does** exist on `storage.Method`, so `$method.asyncResponse` resolves correctly. [VERIFIED:
read source]

**Gotcha 4: `$field.required` vs `isRequired` Kotlin getter**

Kotlin `val isRequired: Boolean` compiles to a JVM method `isRequired()`. Velocity's
JavaBean introspection recognises `is`-prefix boolean getters and resolves `required` from
`isRequired()`. So `$field.required` works correctly. [ASSUMED — JavaBean specification §7.3.3;
standard Velocity behaviour documented since V1]

---

## Standard Stack

No new libraries required. The existing stack handles everything.

| Component | Version | Role |
|-----------|---------|------|
| `velocity-engine-core` | 2.3 | Template rendering — `#if`, `#foreach`, macros |
| `storage.Method.asyncResponse` | — (Phase 1) | Data source for new template block |
| `ObjectInspectionHelper` | — | `hasFields`, `getFields`, `isTypeRef` helper methods |
| `async-response-smoke-test.yaml` | — | Test fixture; reused as-is |

---

## Architecture Patterns

### System Architecture Diagram

```
YAML file
    |
    v
DocValidator.getValidatedDocument()  [storage.Document populated]
    |
    v
VelocityContext
    |- $document  (storage.Document)
    |- $helper    (ObjectInspectionHelper)
    |- $link      (LinkTool)
    |- $text      (PlainTextTool)
    |- $esc       (EscapeTool)
    |
    v
rest_api_doc.vm  [Velocity merge]
    |
    +-- foreach $resource in $document.service.methods
        +-- foreach $method in $resource.actions
            +-- existing: Response, Success Codes, Failure Codes
            +-- NEW: #if ($method.asyncResponse)
                    |
                    +-- #if ($helper.hasFields($method.asyncResponse))
                    |       #fieldheaders + #foreach $field in $helper.getFields(...)
                    +-- #elseif ($helper.isTypeRef($method.asyncResponse))
                            $link.typeSimple($method.asyncResponse.typeRef)
    |
    v
HTML output file
```

### Template Change Location

In `/rest_api_doc.vm`, the new block inserts immediately before the closing `</section>` of
each method (line 172 in the current file), after the Failure Codes `</ul>`.

### Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Field list flattening with include/exclude | Custom traversal | `$helper.getFields(typeSpec)` via `FieldElementList` |
| HTML entity escaping | Manual escaping | `$esc` (EscapeTool from velocity-tools) |
| Type hyperlinking | Custom string replace | `$link.typeSimple(typeName)` / `$link.type(string)` |

---

## Package Legitimacy Audit

No new packages are installed in this phase. This section is not applicable.

---

## Common Pitfalls

### Pitfall 1: Placing the `#if` block inside the wrong section close tag
**What goes wrong:** The new block appears after `</section>` instead of before it, making
the async content render outside the method `<section>` element.
**How to avoid:** The `</section>` on line 172 closes the method. Insert the `#if` block
between the closing `</ul>` of Failure Codes and that `</section>`.
**Warning sign:** Rendered HTML shows async content floating after `</section>` in the DOM.

### Pitfall 2: Forgetting the `#elseif ($helper.isTypeRef(...))` branch
**What goes wrong:** asyncResponse blocks whose shape is a typeref (not inline object) render
nothing — the field table `#if` fails because `hasFields` returns false, but no fallback
exists. `pureAsyncMethod` in the fixture uses a typeref response (not asyncResponse), so this
case isn't tested by the fixture's asyncResponse blocks — however a future spec could have
a typeref asyncResponse.
**How to avoid:** Include the `#elseif ($helper.isTypeRef($method.asyncResponse))` branch as
specified in 03-01-PLAN.md Task 1.

### Pitfall 3: Calling `$helper.getFields($method.asyncResponse)` when `hasFields` is false
**What goes wrong:** `ObjectInspectionHelper.getFields(typeSpec: TypeSpec)` calls
`typeSpec.fields!!` — null-assertion on `fields`. If `asyncResponse` is a typeref shape
(fields is null), calling `getFields` throws a NullPointerException.
**How to avoid:** The `#if ($helper.hasFields($method.asyncResponse))` guard must wrap the
`getFields` call. Always check `hasFields` before `getFields`. This is correctly done in
03-01-PLAN.md Task 1.

### Pitfall 4: Velocity `#if` on a Kotlin `Boolean` property named `is*`
**What goes wrong:** Confusing Kotlin's `isRequired` with how Velocity accesses it. If you
write `#if ($field.isRequired)` in the template, Velocity looks for `getIsRequired()` which
Kotlin does NOT generate (it generates `isRequired()`). The existing template correctly uses
`$field.required` which maps to `isRequired()` via JavaBean `is`-prefix convention.
**How to avoid:** Use `$field.required` (not `$field.isRequired`) when accessing `isRequired`
from Velocity. The existing `#fieldrow` macro already does this correctly.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (already in build.gradle.kts) |
| Config file | `build.gradle.kts`: `tasks.test { useJUnitPlatform() }` |
| Quick run command | `./gradlew test --tests "*.DocGeneratorAsyncHtmlTest"` |
| Full suite command | `./gradlew build` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HTML-01 | Method with asyncResponse renders second response section | integration | `./gradlew test --tests "*.DocGeneratorAsyncHtmlTest"` | Wave 0 |
| HTML-02 | Section heading reads "Async Response (via getAsyncJobStatus)"; prose mentions jr/jobStatus | integration | same | Wave 0 |
| HTML-03 | Field table renders resultUrl, completedAt, analysisResult with same #fieldrow treatment | integration | same | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew build`
- **Phase gate:** `./gradlew build` (all tests green)

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/giffardtechnologies/restdocs/htmlgen/DocGeneratorAsyncHtmlTest.kt` — covers HTML-01/02/03; test body specified in 03-01-PLAN.md Task 2

*(Existing test infrastructure (`AsyncResponseSmokeTest`, JUnit 5, `@TempDir`) covers the
pattern. Only the new test file is missing.)*

---

## Open Questions

1. **`$method.methodString` and `$method.hasParameters`** in the existing template: these
   properties do not exist on `storage.Method` and Velocity renders them as literal
   reference strings / silently-false conditions. This is pre-existing broken behaviour
   that phase 3 does not fix. Worth a follow-up issue but out of scope for this phase.

2. **`$link.type($method.response)` passes a `Response` object to a `String?` parameter:**
   Velocity calls `Response.toString()` on the object, which for a non-data-class
   subclass with no override produces something like `com.giffardtechnologies.restdocs.storage.Response@1a2b3c`.
   This is also pre-existing and out of scope.

---

## Plan Impact

**03-01-PLAN.md requires no changes.** Research confirms:

- Template path (`/rest_api_doc.vm`): correct
- `$method.asyncResponse` accessor: works as expected (null-safe Velocity `#if`)
- `$helper.hasFields($method.asyncResponse)`: dispatches to `TypeSpec` overload via Java
  subtype assignability — correct
- `$helper.getFields($method.asyncResponse)`: same dispatch — correct
- `$helper.isTypeRef($method.asyncResponse)`: `isTypeRef(typeSpec: TypeSpec?)` signature
  accepts nullable — correct
- `DocGenerator(false).generate(yamlFile, templateFile, outputFile, Options(false))`: matches
  actual `DocGenerator` API — correct
- `@BeforeAll` + `@TestInstance(PER_CLASS)` test structure: matches `AsyncResponseSmokeTest`
  pattern — correct
- Test fixture (`async-response-smoke-test.yaml`): covers pureAsyncMethod (inline object
  fields), mixedAsyncMethod (inline object fields), syncMethod (no asyncResponse) — all
  three success criteria covered
- Regression assertion (count of "Async Response" occurrences == 2): valid because the
  template guards on `#if ($method.asyncResponse)`, and syncMethod has `asyncResponse = null`

The plan is accurate and can be executed as written.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Gradle sets `user.dir` to project root during `tasks.test` JVM fork | Q4 — Template path in tests | If wrong, `rest_api_doc.vm` would not be found at test time; test would throw `FileNotFoundException`. Low risk — this is standard Gradle behaviour and the project has no `workingDir` override. |
| A2 | Velocity 2.3 evaluates `#if (nullObject)` as false | Q7 — Gotcha 1 | If wrong, the `#if ($method.asyncResponse)` guard would not behave correctly for sync methods. Low risk — null-as-false is fundamental Velocity behaviour since V1. |
| A3 | `$field.required` maps to `isRequired()` via JavaBean `is`-prefix convention in Velocity | Q7 — Gotcha 4 | If wrong, `#fieldrow` would fail to render required/optional correctly. Low risk — this is pre-existing working template code. |

---

## Sources

### Primary (HIGH confidence)
- `/rest_api_doc.vm` (project root) — complete template read; macro and method section confirmed
- `docs/rest_api_doc.vm` — confirmed as data-objects-only partial with no method section
- `src/main/java/.../DocGenerator.kt` — VelocityEngine init, loader path, context construction
- `src/main/java/.../DocGeneratorCommand.kt` — `user.dir` usage, `rest_api_doc.vm` default path
- `src/main/java/.../storage/Method.kt` — `asyncResponse: Response?` field confirmed at line 51
- `src/main/java/.../storage/Response.kt` — `class Response ... : TypeSpec(...)` inheritance confirmed
- `src/main/java/.../storage/type/TypeSpec.kt` — `fields: ArrayList<FieldListElement>?` property
- `src/main/java/.../storage/type/Field.kt` — `val isRequired: Boolean`, `val name: String`, `val description: String?`
- `src/main/java/.../htmlgen/ObjectInspectionHelper.kt` — `hasFields(TypeSpec)`, `getFields(TypeSpec)`, `isTypeRef(TypeSpec?)` overloads confirmed
- `src/main/java/.../storage/type/FieldElementList.kt` — `getFields()` handles both `Field` and `FieldListIncludeElement`
- `src/test/resources/async-response-smoke-test.yaml` — fixture covers pureAsyncMethod, mixedAsyncMethod, syncMethod with direct inline fields
- `src/test/kotlin/.../codegen/AsyncResponseSmokeTest.kt` — test pattern for `@TempDir`, fixture loading, `String.contains()` assertions
- `build.gradle.kts` — no `workingDir` override on `tasks.test`; Velocity 2.3 / velocity-tools 3.1 confirmed

### Secondary (MEDIUM confidence)
- `src/main/java/.../htmlgen/LinkTool.kt` — `type(String?)` and `typeSimple(String?)` signatures

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Template change location and content: HIGH — full template read and line-level analysis
- Helper method dispatch (subtype): HIGH — ObjectInspectionHelper overload signatures read
- `user.dir` in Gradle test: MEDIUM — standard behaviour, no official doc verified in session
- Velocity null/boolean semantics: MEDIUM — well-known V1+ behaviour, not verified via docs

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (template and storage model changes would invalidate sooner)
