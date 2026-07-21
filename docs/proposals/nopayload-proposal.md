# Proposal: `noPayload` Flag on `Response`

Date: 2026-07-21
Context: Follow-on to the `asyncResponse` feature (3.0). While retroactively documenting
Allego's 52 `AsyncCapableResponse`/`AsyncJobResponse` methods, several never write an async
payload at all, by design (fire-and-forget bulk operations). `type`/`parsedAs`/`description`
already handle "there's a value, here's its shape" (a bare scalar via `parsedAs`, raw text via
`type: string`) — but there's no accurate way to say "there is deliberately no value."

See also [deprecated-proposal.md](docs/proposals/deprecated-proposal.md) — a related but independent change
adding a `deprecated`/`deprecationNote` flag to `Method`, split out separately since the two
facts are orthogonal (a deprecated method isn't necessarily payload-less, and a no-payload
method isn't necessarily deprecated).

---

## Problem

`doc_validator` currently warns whenever a method's `response` has a `job` field but no
`asyncResponse` block, on the theory that this flags undocumented payloads worth investigating.
In practice, a real fraction of async methods never write anything to the async-result cache —
by design (fire-and-forget bulk operations). For these, writing a fake `type: string` block just
to silence the warning is dishonest: it implies a value exists to parse when none ever will.

**"No payload, definitively"** is currently unrepresentable in `reference.yaml` — a positive
statement, not the absence of an `asyncResponse` block, so the validator can tell "checked,
there's nothing" apart from "not yet documented."

See [history.md](history.md) for the rejected
`DataType: none` approach this proposal started from.

---

## Proposed Change: `noPayload` boolean on `Response`

Add a new field to the `Response` data object in `reference.yaml` (currently
`docs/reference.yaml:462-494+`, alongside `type`/`typeref`/`description`):

```yaml
- name: noPayload
  description: No value is ever written for this response. Set this instead of `type`/`typeref`
               to positively document a job (or a synchronous method) that produces no payload,
               as distinct from a response/asyncResponse block that simply hasn't been written
               yet.
  required: no
  type: boolean
  default: false
```

Usage:
```yaml
asyncResponse:
    noPayload: true
    description: No payload is ever written for this job; jr remains empty once jobStatus
                 reaches 0. Only the job's completion status is meaningful.
```

`type`/`typeref` remain absent when `noPayload: true` — `noPayload` is a third alternative to
the existing "either `type` or `typeref` must be present" rule, not a `DataType` value, so it's
satisfied via a `Response`-only `validate()` override (see Validator Changes below) rather than a
change to `DataType` or to `TypeSpec.validate()` itself. `noPayload` is valid on both `response`
and `asyncResponse` — a synchronous
method with no body is exactly as legitimate as an async job with no payload, and both should be
positively documented the same way.

### Kotlin model changes

- `Response` (`src/main/java/com/giffardtechnologies/restdocs/storage/Response.kt:36-51`): add a
  `noPayload: Boolean = false` constructor property. This is a `Response`-level property, not a
  `TypeSpec` one — `Field` and nested `object` members extend `TypeSpec` directly and don't go
  through `Response`, so putting `noPayload` on `Response` instead of the shared `TypeSpec` base
  (`src/main/java/com/giffardtechnologies/restdocs/storage/type/TypeSpec.kt:31-43`) makes it
  structurally unavailable outside `response`/`asyncResponse` — no runtime check needed to keep
  it off nested fields.
- `Response` does not currently override `validate()` — it inherits `TypeSpec.validate()`
  unmodified. Since `noPayload` deliberately lives on `Response`, not `TypeSpec` (see rationale
  above), `TypeSpec.validate()` has no way to see it. Rather than threading a `noPayload`-aware
  property through the shared `TypeSpec` base (which would require an `open` property that every
  other `TypeSpec` subtype/nested `Field`/`object` implicitly inherits as `false`, just to let one
  subclass override it), `Response` should add its own `override fun validate()` that
  short-circuits before delegating to the base implementation:

  ```kotlin
  override fun validate(validationContext: Any?) {
      if (noPayload) {
          if (type != null || typeRef != null) {
              throw ValidationException(
                  "Response cannot have 'noPayload' combined with 'type' or 'typeref'"
              )
          }
          if (parsedAs != null || interpretedAs != null || key != null || flagType != null ||
              items != null || restrictions != null || fields != null || values != null) {
              throw ValidationException(
                  "Response cannot have 'noPayload' combined with substructure fields " +
                  "(parsedAs/interpretedAs/key/flagType/items/restrictions/fields/values)"
              )
          }
          return
      }
      super.validate(validationContext)
  }
  ```

  This keeps `TypeSpec.kt` completely untouched — the "third alternative to the `type`/`typeref`
  presence rule" lives entirely in `Response`'s override, which either handles the `noPayload`
  case itself and returns, or falls through unchanged to the existing base validation for every
  other `Response` (async or sync) that doesn't set `noPayload`. This directly satisfies both
  numbered items under "Validator Changes" below in one override, rather than editing
  `TypeSpec.validate()`'s existing branches in place.
- `domain/Response.kt` (`src/main/java/com/giffardtechnologies/restdocs/domain/Response.kt:6-9`)
  is a second, separate `Response` model — the post-mapping domain object codegen consumes
  (`MethodProcessor.kt`), distinct from the `storage.Response` parsing model above. Its
  `typeSpec: TypeSpec` is non-null today, so it has no way to represent "no payload" once mapped.
  This needs either a nullable `typeSpec: TypeSpec? = null` or its own `noPayload: Boolean = false`
  sibling field, mirroring the storage-side change.
- The mapping in between (`StorageToDomainMappers.kt:541-546`,
  `private fun ResponseStorageModel.mapToModel(context: Context): Response`) unconditionally calls
  `this.mapToModel("response", context)` on the `TypeSpec`, which assumes `type`/`typeref` is
  present. This needs a `noPayload` branch that skips the `TypeSpec` mapping and produces a
  `domain.Response` with a null `typeSpec`/`noPayload: true` instead of attempting to map an
  absent type.

### Codegen consumption (`MethodProcessor.kt`)

With `domain.Response.typeSpec` made nullable, two call sites need attention:

- `getClassNames()` (`MethodProcessor.kt:174-203`): the sync-`response` branch
  (lines 178-190) already degrades gracefully — a `when (response.typeSpec)` on a null typeSpec
  falls through to `else -> null`, and the `?: Unit::class.asClassName()` on line 190 then
  produces a `Unit` response class, which is exactly the right behavior for `noPayload`. No
  change needed there. The `asyncResponse` branch (lines 192-200) is **not** safe as written:
  `else -> fieldAndTypeProcessor.getScalarTypeName(spec)` would receive `spec = null` for a
  `noPayload` async response and call `getScalarTypeName(null)`, which isn't designed for a null
  input. Needs an explicit `null -> Unit::class.asClassName()` branch (mirroring the sync-response
  fallback) ahead of the `else`.
- `createResponseClassDefinition()` (`MethodProcessor.kt:409-427`) and
  `createAsyncResponseClassDefinition()` (`MethodProcessor.kt:429-447`): both already fall to
  `else -> null` for any non-`ObjectSpec` typeSpec, which a null typeSpec also matches — no
  generated class body is needed for `noPayload` either way, so these are fine unchanged.
- Net effect: a `noPayload: true` response/asyncResponse should generate the same way an
  already-supported scalarless/empty response does today — a `Unit`-typed response with no
  generated class body — once the one `null ->` branch above is added.

---

## Validator Changes (`doc_validator`)

1. **`Response.validate()` override** (see Kotlin model changes above) satisfies the
   `type`/`typeref` presence rule for `noPayload: true` without touching
   `TypeSpec.validate()` (`src/main/java/com/giffardtechnologies/restdocs/storage/type/TypeSpec.kt:57-167`)
   at all: when `noPayload` is set, the override rejects it alongside `type`/`typeref` and returns
   early instead of falling through to the base "must have one of [type, typeref]" check at lines
   164-166 — `noPayload: true` satisfies the rule by never reaching that throw, not by extending
   its condition.
2. **Reject `items`/`key`/`values`/`fields`/`parsedAs`/`interpretedAs`/`flagType`/`restrictions`
   alongside `noPayload: true`** — the scalar-type branches of `TypeSpec.validate()` (lines 66-78)
   currently don't check for stray substructure fields at all (confirmed: no existing rejection
   for e.g. `items` set alongside `type: string` today), so this is new validation, not a pattern
   extension. It lives entirely in `Response`'s override (see above) rather than retrofitting
   scalar types generally in the shared base, keeping this proposal scoped to `Response` as
   intended.
3. **The existing "response has a job field but no asyncResponse block" warning**
   (`Method.kt:79-93`, specifically the `VALID-03` check at lines 88-91) already suppresses
   whenever `asyncResponse != null` — `noPayload: true` on a present `asyncResponse` block
   satisfies this for free once (1) lands. No change needed here beyond making `noPayload` a
   legal value.

---

## Non-goals

- An absent `response`/`asyncResponse` block continues to implicitly mean "no payload," as it
  does today — this proposal does not change that behavior or add a warning for it. The intent
  is for `noPayload: true` to eventually become the explicit, preferred way to say this, with the
  validator warning on the implicit (block-omitted) form to nudge authors toward it — but that
  validator change is a follow-on, not part of this proposal.
- No change to how `parsedAs`/`type: string` document bare scalars or raw text — those already
  work (see Allego's `copyCollectionShallow`/`copyCollectionWithoutCredentials` for `parsedAs`,
  `generateAiDescriptionForContent`/`generateAiSparkDocs` for raw-text `type: string`).
- `noPayload` is not added to `DataType` and is not usable on nested `Field`s or `object`
  members — it lives on `Response` only, so both `response` and `asyncResponse` accept it (a
  synchronous method with no body and an async job with no payload are equally legitimate).

---

## Example

```yaml
- name:        removeGenAIAssistant
  id:          NNN
  method:      POST
  description: Removes a configured GenAI assistant.
  parameters:
      - ...
  response:
      typeref: AsyncJobResponse
  asyncResponse:
      noPayload:   true
      description: No payload is ever written; jr remains empty once jobStatus reaches 0.
```
