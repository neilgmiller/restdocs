# Proposal: `deprecated` / `deprecationNote` / `deprecatedSince` on `Method`

Date: 2026-07-21
Context: Follow-on to the `asyncResponse` feature (3.0). While retroactively documenting
Allego's 52 `AsyncCapableResponse`/`AsyncJobResponse` methods, several are deprecated no-op
handlers kept only for backward compatibility. There's currently no way to mark a method as
deprecated in `reference.yaml` other than a free-text note in `description`, which the
validator has no way to check for completeness.

See also the `noPayload` flag on `Response` (already shipped — `storage/Response.kt:42`,
documented in `docs/reference.yaml:474-481`) — a related but independent fact, kept as a
separate field since the two facts are orthogonal: a deprecated method isn't necessarily
payload-less (it could still return a real value while discouraged), and a no-payload method
isn't necessarily deprecated (fire-and-forget bulk operations are payload-less by design, not
retirement).

---

## Problem

**"This method is deprecated/no-op"** is a method-level fact, independent of payload shape (a
deprecated method could theoretically still have had a real payload before retirement), and is
currently unrepresentable in `reference.yaml` except as unstructured prose in `description`.

---

## Proposed Change: `deprecated` / `deprecationNote` / `deprecatedSince` on `Method`

Add three fields to the `Method` data object (`docs/reference.yaml:380-461`), siblings of
`response`/`asyncResponse`:

```yaml
- name: deprecated
  description: Marks this method as deprecated. Deprecated methods should still declare an
               accurate response/asyncResponse — for retired async handlers with no payload,
               set 'noPayload' on the response instead of omitting it.
  required: no
  type: boolean
  default: false
- name: deprecationNote
  description: Human-readable reason for the deprecation. Required whenever 'deprecated' is
               true, and invalid to set unless 'deprecated' is also true — a bare
               'deprecated: true' with no explanation is uninformative.
  required: no
  type: string
- name: deprecatedSince
  description: ISO-8601 date (YYYY-MM-DD) the method was deprecated; rejected if it is in the
               future. Optional even when 'deprecated' is true, but strongly recommended when
               known — omitting it produces a warning, because any codegen tooling that skips
               generating old deprecated methods treats an absent date as "deprecated an
               unknown, arbitrarily long time ago," making the method immediately eligible to
               be dropped rather than exempt from that check.
  required: no
  type: string
  sampleValues:
      - 2026-08-01
```

Kotlin model: `Method` data class fields, `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt:36-56`
— add `deprecated: Boolean = false`, `deprecationNote: String? = null`, and
`deprecatedSince: String? = null` alongside the existing `response`/`asyncResponse` properties.
`deprecatedSince` stays a plain `String` in the storage model, consistent with `deprecationNote`
and the rest of the parsing layer — `JacksonMapperBuilder.kt` currently wires only `kotlinModule()`,
a custom boolean deserializer, and `ValidationModule` (no `kotlinx-datetime` Jackson module is
registered today), so a `LocalDate`-typed property would fail to deserialize without adding one.
Parsing to `kotlinx.datetime.LocalDate` (already a project dependency, currently unused in `src/main`)
happens explicitly where the date is consumed — the validator (format/not-future checks, below)
and the codegen cutoff comparison (see Codegen Changes) — rather than at the Jackson layer, so a
malformed date surfaces as a `ValidationException` with a clear message instead of a raw parse
failure during YAML deserialization. Note
`src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt` does not disable
`FAIL_ON_UNKNOWN_PROPERTIES` (Jackson's default), so all three fields must land in the model
before any doc using them will parse — there's no silent-ignore fallback to rely on in the
meantime.

This is deliberately orthogonal to payload shape: `deprecated` describes the method's status;
what it returns (including whether it returns nothing) is described separately by
`response`/`asyncResponse`. A method can be deprecated-but-still-functional (real payload, just
discouraged) or deprecated-and-no-op — the two facts compose rather than overload one field.

---

## Validator Changes (`doc_validator`)

1. **Recognize `deprecated`/`deprecationNote`/`deprecatedSince`** as valid `Method`-level keys —
   since `FAIL_ON_UNKNOWN_PROPERTIES` is on by default (Jackson's default; see
   `JacksonMapperBuilder.kt`), simply adding the fields to the `Method` data class (Proposed
   Change above) is sufficient; no separate whitelist step exists to update.
2. **Require `deprecationNote` whenever `deprecated: true`** — add this check to
   `Method.validate()` (`Method.kt:62-77`), throwing a `ValidationException` rather than emitting
   a warning. Making this a hard requirement (not optional) keeps a "no undocumented gaps"
   spirit: a bare `deprecated: true` with no explanation is uninformative to future readers.
3. **Reject `deprecationNote`/`deprecatedSince` when `deprecated` is not `true`** — same check,
   same location. A `deprecationNote`/`deprecatedSince` left on a method after `deprecated` is
   reverted to `false` (or never set) is a dangling, misleading note; the fields should only ever
   be set together.
4. **Validate `deprecatedSince` format and range, when present** — same location. Parse with
   `kotlinx.datetime.LocalDate.parse()`, throwing a `ValidationException` (not a raw
   `IllegalArgumentException`) on malformed input, and reject a `deprecatedSince` date that is in
   the future relative to generation time — a method can't be deprecated before it's deprecated.
   `deprecatedSince` remains optional even when `deprecated: true` (see field description above);
   this rule only fires when the field is present.
5. **Warn when `deprecated: true` and `deprecatedSince` is absent** — add to the two-arg
   `Method.validate(validationContext, warningEmitter)` override (`Method.kt:79-93`), alongside
   the existing `VALID-03`/`VALID-04` checks, rather than the single-arg `validate()` used by
   rules 2-4: those are hard `ValidationException`s, but a missing `deprecatedSince` shouldn't
   block validation the way a missing `deprecationNote` does — a date genuinely isn't always
   known, especially for methods being retroactively documented (like the 52
   `AsyncCapableResponse`/`AsyncJobResponse` methods motivating this proposal, whose actual
   deprecation dates predate any record of them). It's still worth flagging: unlike an ordinary
   optional field, an absent `deprecatedSince` is *not* inert with respect to codegen — per the
   field description above, it makes the method immediately skip-eligible (treated as
   arbitrarily old), the opposite of "no date recorded yet, nothing happens." The warning is the
   only friction pushing an author toward recording a real date instead of leaving the method
   perpetually skip-eligible by omission.

---

## Codegen Changes (`kotlin_generator`): skip old deprecated methods, annotate surviving ones

Goal: give codegen an option to omit generating request/response classes for methods that have
been deprecated for a while, without requiring `reference.yaml` authors to delete or comment out
the method entry itself (which would also drop it from generated docs and from validation).
Methods that stay deprecated but survive the cutoff — or when no cutoff is configured at all —
should still come out of codegen marked `@Deprecated`, so consumers of the generated client code
get an IDE-visible warning instead of no signal at all.

1. **Propagate `deprecated`/`deprecationNote`/`deprecatedSince` into the domain model.**
   `domain.Method` (`src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt:6-21`)
   currently has no equivalent fields — codegen (`MethodProcessor`, `KotlinGenerator`) reads only
   this domain model, not the storage model, so today it can't see any of the three. Add
   `val deprecated: Boolean = false`, `val deprecationNote: String? = null`, and
   `val deprecatedSince: LocalDate? = null` to `domain.Method`, and populate them in
   `MethodStorageModel.mapToModel()`
   (`src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt:512-529`),
   parsing the storage model's `String` `deprecatedSince` to `kotlinx.datetime.LocalDate` at this
   mapping boundary — the validator (above) has already guaranteed it parses cleanly by the time
   code generation runs, so this parse is not expected to fail in practice. All three fields serve
   two independent downstream uses: the skip filter (below) and the `@Deprecated` annotation
   (item 6) on methods that aren't filtered out — `deprecationNote` specifically only exists on
   the domain model for the latter; the filter itself only needs `deprecated`/`deprecatedSince`.
2. **Move parsing, validation, and filtering out of `KotlinGenerator` and into
   `KotlinGeneratorCommand`.** Change `KotlinGenerator.generate()` from
   `generate(sourceFile: File, options: Options)`
   (`src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt:40`) to
   `generate(document: Document, options: Options)`. `KotlinGeneratorCommand` now calls
   `DocValidator().getValidatedDocument(sourceFile).mapToModel()` itself, filters
   `document.service.methods` (rule 4, below) to drop skip-eligible methods, and passes the
   already-filtered `Document` in. `KotlinGenerator` no longer has any deprecation-awareness for
   filtering purposes — it just processes whatever methods are in the `Document` it's handed,
   which is also a more flexible shape for the generator to expose for other callers in the
   future.

   This also resolves an interaction that an earlier draft of this proposal missed:
   `KotlinGenerator.generate()` independently looks up the method configured by
   `asyncJobStatus.methodID` (`KotlinGenerator.kt:116-119`) to build the `DecodeAsyncResult`
   helper, via `document.service?.methods?.firstOrNull { m -> m.id == config.methodID }` followed
   by `methodProcessor.getClassNames(it)`. `getClassNames` computes class names structurally from
   the method's declared type — it doesn't check whether `processMethod` actually ran for that
   method. If filtering had instead happened only inside the per-method generation loop, a
   skip-eligible method configured as the job-status endpoint could be skipped by the loop while
   still being found and referenced by this separate lookup, producing a generated file that
   references response classes that were never written to disk — a silent build break. With a
   single filter applied once, before `KotlinGenerator` ever sees the document, both consumers see
   the same already-pruned method list: if the configured `asyncJobStatus.methodID` itself was
   filtered out, the existing
   `?: error("asyncJobStatusConfig.methodID=${config.methodID} not found in document service
   methods")` fallback (already in the codebase, `KotlinGenerator.kt:119`) fires immediately with
   a clear message, instead of the silent breakage above.

   The same single-filter-point fix also covers two other direct `document.service.methods`
   readers that this proposal doesn't otherwise touch: the `SwiftAPIServerClient` generation loop
   (`KotlinGenerator.kt:173`, which builds `execute`/`fetch...AsyncResponse` wrapper functions per
   method) and `DataObjectUsageClassifier` (`DataObjectUsageClassifier.kt:47`, which drives whether
   a `DataObject` is generated as top-level vs. hidden). Both iterate `document.service.methods`
   directly rather than going through `processMethod`, so both would have the same
   silently-stale-reference risk if filtering happened inside the per-method loop instead of on the
   `Document` itself. Filtering once, before either consumer runs, means they simply never see a
   skip-eligible method — no separate fix needed for either.

   One side effect of this is worth calling out on purpose rather than leaving implicit: if a
   `DataObject` is referenced only by a skip-filtered deprecated method, `DataObjectUsageClassifier`
   will no longer count it as used, and its class may stop being generated (or be reclassified as
   hidden) even though it's still declared in `reference.yaml`. This is the intended outcome — a
   fully skipped method's request/response types shouldn't be generated either — but it is a
   behavior change (a type's presence in generated code becomes dependent on `skipDeprecatedBefore`
   at build time, not just on `reference.yaml` contents) and should be called out as such rather
   than discovered later.
3. **Add a cutoff option to `KotlinGeneratorCommand`** — not `KotlinGenerator.Options`, per the
   move in item 2 — a new `skipDeprecatedBefore: LocalDate? = null`, used only by the command's
   own filter step. `null` (the default) preserves today's behavior — every method is generated
   regardless of deprecation status.
4. **Filter in `KotlinGeneratorCommand`, before calling `generate()`:** drop methods where
   `it.deprecated`, `skipDeprecatedBefore != null`, and
   `(it.deprecatedSince ?: LocalDate.MIN) < skipDeprecatedBefore`. The `skipDeprecatedBefore !=
   null` guard is required regardless — `LocalDate` isn't comparable to a nullable `LocalDate?`,
   so without it the common case (option unset, default `null`) fails to compile, not just fails
   to filter. `it.deprecatedSince ?: LocalDate.MIN` (rather than requiring
   `it.deprecatedSince != null` as a separate guard) is the deliberate choice from the
   `deprecatedSince` discussion above: a deprecated method with no recorded date is treated as
   arbitrarily old, so it's skip-eligible under *any* cutoff, not exempted from this option — the
   field description's warning (Validator Changes, rule 5) is what nudges authors toward
   recording a real date instead. Log which methods were skipped (name + `deprecatedSince`,
   printing "unknown" for a null date), gated behind the existing `verboseLogging` option — this
   would be that option's first real use in the Kotlin generation path (it's currently declared
   in `KotlinGenerator.Options` but never read); unconditional logging would print a line for
   every skipped method on every build, which is noisy at the scale of the ~52 methods motivating
   this proposal.
5. **Expose the cutoff via `kotlin-gen.properties`**
   (`src/main/java/com/giffardtechnologies/restdocs/KotlinGeneratorCommand.kt:86-89`): a new
   `skipDeprecatedBefore` property, parsed the same way `asyncJobStatus.methodID` is today (read
   as a string, parsed and validated, `error(...)` on a malformed value) and used by the command's
   own filter step (item 4). Absent property → `null` → no filtering.
6. **Annotate surviving deprecated methods in generated code.** In
   `MethodProcessor.processMethod()` (`MethodProcessor.kt:212` onward), when `method.deprecated`
   is true, add a `kotlin.Deprecated` annotation to the generated request class
   (`requestClassBuilder`, `MethodProcessor.kt:222`) with `message = method.deprecationNote`.
   `kotlin.Deprecated.message` has no default — this is exactly why `deprecationNote` needs to
   ride along on `domain.Method` (item 1) rather than just `deprecated`/`deprecatedSince`. This
   only fires for methods that reach `processMethod` at all — a method skip-filtered out by item 4
   never gets here, so there's no conflict between "skipped" and "annotated": a method is either
   absent from the generated code entirely, or present and marked `@Deprecated`.

This option only affects `kotlin_generator` output — it does not change `doc_validator`'s
behavior; a method skipped for codegen is still fully validated (see Non-goals). It also doesn't
change `doc_generator`'s output — deprecated methods, old or not, always appear in generated
docs; see Doc Generator Changes below for how they're surfaced there.

---

## Doc Generator Changes (`doc_generator`): surface deprecation in generated docs

`doc_generator` reads the storage model directly (`DocGenerator.generateHTML()` passes
`DocValidator().getValidatedDocument(sourceFile)` — the same `storage.Document`/`storage.Method`
this proposal adds fields to — straight into the Velocity context as `document`), so
`$method.deprecated`/`$method.deprecationNote`/`$method.deprecatedSince` are already available to
the template once the Proposed Change lands, with no extra plumbing (unlike `kotlin_generator`,
which needed the domain-model propagation in Codegen Changes above).

1. **Add helper methods to `ObjectInspectionHelper`**
   (`src/main/java/com/giffardtechnologies/restdocs/htmlgen/ObjectInspectionHelper.kt`), following
   the existing `hasDescription`/`hasNoPayload` pattern (e.g. line 131/135): `fun isDeprecated(method:
   Method): Boolean = method.deprecated` and `fun hasDeprecationNote(method: Method): Boolean =
   !method.deprecationNote.isNullOrEmpty()`. The template already accesses `$method.deprecated`
   directly for booleans elsewhere (see `$helper.hasNoPayload($method.response)` for the sibling
   pattern), so a thin helper wrapper here is for consistency with the rest of the file, not a
   strict requirement — `#if ($method.deprecated)` would also work directly in Velocity.
2. **Render a badge in the method header**
   (`rest_api_doc.vm:363`, the `<h4 id="$method.name">$method.name...(id: $method.id)</h4>`
   line): add a conditional badge span after the existing id span, e.g.
   ```
   #if ($method.deprecated)
   <span style="color: #a00; font-weight: bold; margin-left: .5em;">DEPRECATED</span>
   #end
   ```
   so deprecation is visible at a glance when scanning the method list, not just when reading the
   full body.
3. **Render the note and date in the method body**, near the existing description paragraph
   (`rest_api_doc.vm:364`, `<p>$helper.getDescription($method)</p>`): when `$method.deprecated`,
   add a paragraph with `$method.deprecationNote` (always present per the validator's hard
   requirement) and, when present, `$method.deprecatedSince` — e.g. "Deprecated since
   2026-08-01: GenAI Assistants API retired August 2026." A missing `deprecatedSince` (validator
   rule 5 only warns, doesn't block) should degrade to just the note, not print an empty/null
   date.

This is presentation-only — it doesn't change what `doc_generator` validates or what data it
reads, only how the already-parsed fields are rendered.

---

## Non-goals

- `deprecated` is scoped to `Method` only in this proposal — not `Field`/`DataObject` — since the
  motivating cases are all whole-method retirements (e.g. the GenAI Assistants API). A
  field-level or object-level `deprecated` could be a separate follow-on if a similar need shows
  up there.
- Deprecating a method does not change any other validation behavior — deprecated methods'
  `parameters`/`response`/`asyncResponse` are validated identically to non-deprecated ones.
- No automatic default for `skipDeprecatedBefore` (e.g. "always skip anything deprecated more
  than a year ago") — the cutoff is only ever an explicit, author-supplied date via
  `kotlin-gen.properties`. An implicit rolling cutoff would make generated output depend on
  *when* codegen runs rather than on the source doc, which is a surprising failure mode for a
  generator.
- No equivalent skip option for `doc_generator` — deprecated methods (old or not) always appear
  in generated docs (with a badge, per Doc Generator Changes); the age-based skip option is
  codegen-only (see Codegen Changes). Omitting deprecated methods from docs entirely isn't the
  goal here — the point of documenting them is so they're still discoverable, just visibly
  flagged as retired.
- No deprecation marker in the table-of-contents / method-list sidebar (`rest_api_doc.vm`'s
  `#foreach( $resource in $document.service.methods )` block) — only the per-method header and
  body (Doc Generator Changes) are updated in this pass. A reader scanning the TOC alone won't see
  deprecation status without clicking into the method. Deferred rather than decided against; can
  be added later as a small follow-on if it turns out to matter in practice.
- No wiring to make the skip-logging in Codegen Changes item 4 actually reachable. That item gates
  its "which methods were skipped" logging behind `options.verboseLogging`, but
  `KotlinGeneratorCommand.kt:88` currently hardcodes `false` for that argument — there's no
  `kotlin-gen.properties` key feeding it, unlike `asyncJobStatus.methodID`
  (`KotlinGeneratorCommand.kt:79-84`), which is read from properties. As written, the flag can never
  be set to `true`, so the logging is dead code. Deferred rather than folded into this proposal:
  wiring a `verboseLogging` (or similarly-named) properties key into `KotlinGeneratorCommand`,
  read the same way `asyncJobStatus.methodID` is, is a small, separable follow-on.

---

## Example

Based on `removeGenAIAssistant` (deprecated no-op, no payload — combines with the `noPayload`
flag on `Response`):

```yaml
- name:        removeGenAIAssistant
  id:          NNN
  method:      POST
  description: Removes a configured GenAI assistant.
  deprecated:      true
  deprecationNote: GenAI Assistants API retired August 2026.
  deprecatedSince: 2026-08-01
  parameters:
      - ...
  response:
      typeref: AsyncJobResponse
  asyncResponse:
      noPayload:   true
      description: No payload is ever written; the handler is a retired no-op and jr remains
                   empty once jobStatus reaches 0.
```

`description` stays scoped to what the endpoint functionally does; the deprecation-specific facts
(that it's deprecated, why, and since when) live only in `deprecated`/`deprecationNote`/
`deprecatedSince`, not duplicated as prose in `description` too. This also matters for the 52
methods motivating this proposal, whose existing `description` fields already carry deprecation
prose like the old version of this example did — those should be trimmed down to the functional
description as `deprecated`/`deprecationNote`/`deprecatedSince` are added, rather than left
saying the same thing twice.
