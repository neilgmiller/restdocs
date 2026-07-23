# Deprecated Method Fields — Commit a978652

**Branch:** `allego`
**Date:** 2026-07-23
**Files changed:** 16 (+758 / −9)

---

## Overview

While retroactively documenting Allego's 52 `AsyncCapableResponse`/`AsyncJobResponse` methods,
several turned out to be deprecated no-op handlers kept only for backward compatibility. There
was no structured way to mark a `Method` as deprecated in `reference.yaml` — only free-text in
`description`, which the validator had no way to check for completeness. This work adds
`deprecated`/`deprecationNote`/`deprecatedSince` fields to `Method` and threads them end to end
through the storage model, validator, domain model, codegen, and HTML doc template — plus an
optional codegen-only cutoff that can drop long-deprecated methods from generated client code
entirely.

This is mechanism only: migrating the actual 52 methods' `reference.yaml` entries to use the new
fields is separate follow-up work, not part of this change.

---

## The Problem

`deprecated`/`deprecationNote`/`deprecatedSince` is a method-level fact, independent of payload
shape (a deprecated method could still return a real value; a no-payload method isn't
necessarily deprecated). With no structured field for it, deprecation notices lived only as
prose inside `description`, which meant:

- The validator couldn't enforce that a deprecation was actually explained.
- Generated Kotlin client code gave callers no IDE-visible warning that a method was deprecated.
- Generated HTML docs had no visual signal distinguishing a deprecated method from an active one.
- There was no way to eventually stop generating client code for very old deprecated methods
  without deleting their `reference.yaml` entry (which would also drop them from validation and
  docs).

---

## What Was Built

### Schema + storage validation (`reference.yaml`, `storage/Method.kt`)

Added three fields to the `Method` object, modeled on the existing `Response.noPayload` field's
style:

```yaml
- name: deprecated
  type: boolean
  default: false
- name: deprecationNote
  type: string
- name: deprecatedSince
  type: string
  sampleValues: [2026-08-01]
```

`storage.Method` gained matching constructor properties (`deprecatedSince` stays a plain
`String`, parsed to `kotlinx.datetime.LocalDate` only where consumed — the validator and
codegen — not at the Jackson layer, since no `kotlinx-datetime` Jackson module is registered
today).

Validation, split across `Method`'s two `validate()` overrides:

- **Hard errors** (single-arg `validate()`): `deprecationNote`/`deprecatedSince` set while
  `deprecated` is not `true`; `deprecationNote` missing or blank while `deprecated` is `true`;
  `deprecatedSince` present but not a parseable ISO-8601 date; `deprecatedSince` in the future.
- **Warning, not an error** (two-arg `validate(context, warningEmitter)`, alongside the existing
  VALID-03/VALID-04 checks): `deprecated` is `true` but `deprecatedSince` is absent. This has to
  be a warning rather than a hard requirement because a real deprecation date genuinely isn't
  always known, especially for methods being retroactively documented — exactly the case
  motivating this work.

### Domain model + mapping (`domain/Method.kt`, `mappers/StorageToDomainMappers.kt`)

The three fields propagate into `domain.Method` (`deprecatedSince` as `LocalDate?` here), with
`MethodStorageModel.mapToModel()` parsing the storage model's `String` to `LocalDate` — safe to
do unguarded, since by the time `mapToModel()` runs the validator has already guaranteed the date
parses.

### Codegen annotation (`codegen/MethodProcessor.kt`)

When `method.deprecated` is true, `processMethod()` adds a `kotlin.Deprecated` annotation to the
generated request class, with `message = method.deprecationNote`. This is why `deprecationNote`
had to ride along on the domain model, not just `deprecated`/`deprecatedSince` — `Deprecated`'s
`message` parameter has no default.

### Doc generation (`htmlgen/ObjectInspectionHelper.kt`, root `rest_api_doc.vm`)

Added `isDeprecated`/`hasDeprecationNote`/`hasDeprecatedSince` helpers, and rendered a red
"DEPRECATED" badge next to the method name plus a note paragraph with the reason and (when
known) the date. Used an inline `style` attribute rather than a new CSS class, since
`tools/copy-doc-template.sh` only copies the `.vm` file into the deployed Allego server repo, not
`css/api.css` — a new class name would require an out-of-band edit to a file this repo doesn't
own. All template changes target the root-level `rest_api_doc.vm` only; a stale, out-of-date
`docs/rest_api_doc.vm` copy (missing async/`noPayload` support entirely, and what
`docs/docbuild.properties` actually resolves to) was left untouched as a pre-existing, unrelated
issue.

### Codegen skip-filter (`KotlinGenerator.kt`, `KotlinGeneratorCommand.kt`)

Added an optional `skipDeprecatedBefore.date` property (read from `kotlin-gen.properties`,
parsed the same way `asyncJobStatus.methodID` already is) that drops methods from generated
Kotlin output once they've been deprecated long enough. A method with `deprecated: true` and a
`deprecatedSince` before the configured cutoff is skipped; a method with no recorded
`deprecatedSince` is always treated as skip-eligible once filtering is enabled, since an unknown
date can't be proven recent. `null` (the property absent) preserves today's behavior exactly —
every method is generated regardless of deprecation status.

This required changing `KotlinGenerator.generate()` from taking a raw `sourceFile: File` to
taking an already-validated `Document`, moving `DocValidator`/`mapToModel()` out to the caller.
The filter itself runs as the **first statement inside `generate()`**, shadowing its own
`document` parameter:

```kotlin
fun generate(document: Document, options: Options) {
    val document = document.filterDeprecatedMethods(options.skipDeprecatedBefore, options.verboseLogging)
    ...
}
```

This placement matters because `document` is read independently by three separate consumers
further down in `generate()` — the `asyncJobStatusConfig` methodID lookup, the
`SwiftAPIServerClient` generation loop, and `DataObjectUsageClassifier`. Filtering once, before
any of them run, guarantees all three see a consistent, already-pruned method list without three
separate fixes, and without callers of `generate()` having to remember to filter first
themselves. (An earlier iteration of this change put the filter in `KotlinGeneratorCommand`,
before calling `generate()` — functionally equivalent, but moved into `generate()` itself so the
guarantee holds for any future caller too, not just this one.)

`verboseLogging` (an existing but previously dead `Options` field) now gates a log line for each
skipped method; wiring it to an actual `kotlin-gen.properties` key remains out of scope (see
Non-goals) — it stays hardcoded `false`, same as before this change.

---

## Non-goals

- No `Field`/`DataObject`-level `deprecated` — `Method` only; the motivating cases are all
  whole-method retirements.
- No change to how a deprecated method's other fields (`parameters`/`response`/`asyncResponse`)
  validate.
- No implicit default for `skipDeprecatedBefore` — explicit `kotlin-gen.properties` key only, so
  generated output depends on the source doc, not on *when* codegen happens to run.
- No skip option for `doc_generator` — deprecated methods always appear in generated docs
  (with the badge), regardless of age.
- No deprecation marker in the TOC/sidebar method list — only the per-method header and body.
- No wiring of `verboseLogging` to a properties key — stays dead code, as it was before this
  change.
- No migration of the actual 52 `reference.yaml` method entries that motivated this work —
  separate follow-up.

---

## Tests

- `MethodValidationTest` (new) — direct unit tests of `Method.validate()`, bypassing Jackson:
  every hard-error case (note without `deprecated`, `deprecated` without note, blank note,
  malformed date, future date) plus the warning-only missing-date case and the valid-past-date
  passing case.
- `MethodProcessorDeprecatedTest` (new) — asserts `processMethod` emits `@Deprecated(message =
  ...)` when `method.deprecated`, and omits it otherwise.
- `KotlinGeneratorCommandSkipDeprecatedTest` (new) — integration test driven through the real
  picocli entry point (`CommandLine(KotlinGeneratorCommand()).execute(...)`), against a new
  `deprecated-method-test.yaml` fixture with one method deprecated with a date, one deprecated
  with no date, and one active method. Verifies both that a configured cutoff skips the right
  methods and that omitting the property preserves today's generate-everything behavior.
- `DocGeneratorDeprecatedHtmlTest` (new) — renders the same fixture through the real
  `rest_api_doc.vm` and asserts the badge/note appear only for deprecated methods.
- `AsyncResponseSmokeTest` (updated) — adjusted for the `generate()` signature change
  (validate+map now happens in the test setup rather than inside `generate()`).

`./gradlew compileKotlin compileTestKotlin` and `./gradlew test` both passed, 99/99 tests green,
no regressions in existing suites (`DocGeneratorAsyncHtmlTest`, `MethodProcessorAsyncResponseTest`,
`MethodProcessorTypeRefTest`, `DataObjectProcessorTest`, `DataObjectUsageClassifierTest`) — all
construct `Method(...)` with named arguments and rely on the new fields' defaults, so they were
unaffected by the additions.
