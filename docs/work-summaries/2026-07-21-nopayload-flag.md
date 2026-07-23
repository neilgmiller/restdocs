# noPayload Flag on Response — Commit 91fcc0d

**Branch:** `allego`
**Date:** 2026-07-21
**Files changed:** 7 (+49 / −5)

---

## Overview

Follow-on to the `asyncResponse` feature (3.0). While retroactively documenting Allego's 52
`AsyncCapableResponse`/`AsyncJobResponse` methods, several never write an async payload at all,
by design (fire-and-forget bulk operations). `type`/`parsedAs`/`description` already handled
"there's a value, here's its shape," but there was no accurate way to say "there is deliberately
no value." This work added a `noPayload` boolean to `Response` to close that gap, threaded end
to end through the storage model, validator, domain model, codegen, and HTML doc template.

See also `deprecated-proposal.md` — a related but independent change adding a
`deprecated`/`deprecationNote` flag to `Method`, kept separate since the two facts are
orthogonal (a deprecated method isn't necessarily payload-less, and a no-payload method isn't
necessarily deprecated).

---

## The Problem

`doc_validator` warns whenever a method's `response` has a `job` field but no `asyncResponse`
block, on the theory that this flags undocumented payloads worth investigating. In practice, a
real fraction of async methods never write anything to the async-result cache — by design. For
these, writing a fake `type: string` block just to silence the warning would be dishonest: it
implies a value exists to parse when none ever will. "No payload, definitively" was previously
unrepresentable in `reference.yaml` — a positive statement, not the absence of an
`asyncResponse` block, so the validator couldn't tell "checked, there's nothing" apart from
"not yet documented."

---

## What Was Built

### `noPayload` on `Response` (`reference.yaml`, `storage/Response.kt`)

Added `noPayload: Boolean = false` to the `Response` data object in `reference.yaml`, and the
matching constructor property on `storage.Response`. It lives on `Response`, not the shared
`TypeSpec` base — `Field` and nested `object` members extend `TypeSpec` directly and don't go
through `Response`, so this placement makes `noPayload` structurally unavailable outside
`response`/`asyncResponse` with no runtime check needed. It is valid on both `response` and
`asyncResponse` — a synchronous method with no body is exactly as legitimate as an async job
with no payload.

```yaml
asyncResponse:
    noPayload: true
    description: No payload is ever written for this job; jr remains empty once jobStatus
                 reaches 0. Only the job's completion status is meaningful.
```

### Validation (`storage/Response.kt`)

`Response` previously inherited `TypeSpec.validate()` unmodified. Rather than threading a
`noPayload`-aware property through the shared `TypeSpec` base — which would require an `open`
property every other `TypeSpec` subtype/nested `Field`/`object` implicitly inherits as `false`,
just to let one subclass override it — `Response` now has its own `override fun validate()`
that short-circuits before delegating to the base implementation:

- Rejects `noPayload: true` combined with `type` or `typeref`.
- Rejects `noPayload: true` combined with any substructure field (`parsedAs`, `interpretedAs`,
  `key`, `flagType`, `items`, `restrictions`, `fields`, `values`) — new validation; the scalar
  branches of `TypeSpec.validate()` didn't previously check for stray substructure fields at
  all.
- Otherwise falls through to `super.validate()` unchanged.

This keeps `TypeSpec.kt` untouched — `noPayload` acts as a third alternative to the existing
"either `type` or `typeref` must be present" rule entirely from within `Response`'s override,
by returning before that rule is ever reached rather than by extending its condition.

The existing `Method.kt` `VALID-03` check ("response has a job field but no asyncResponse
block") already suppresses whenever `asyncResponse != null`, so `noPayload: true` on a present
`asyncResponse` satisfies it for free — no change was needed there.

### Domain model and mapping (`domain/Response.kt`, `mappers/StorageToDomainMappers.kt`)

`domain.Response.typeSpec` was made nullable (`TypeSpec?`) and a mirroring `noPayload: Boolean`
field was added. The storage-to-domain mapper now skips `TypeSpec` mapping entirely when
`noPayload` is set, producing a domain `Response` with a null `typeSpec` instead of attempting
to map an absent type.

### Codegen (`codegen/MethodProcessor.kt`)

With `typeSpec` now nullable, the sync-`response` branch of `getClassNames()` already degraded
gracefully — its existing `else -> null` plus `?: Unit::class.asClassName()` fallback produces a
`Unit` response class for a null typeSpec, exactly the right behavior. The `asyncResponse`
branch was not safe as written (`else -> fieldAndTypeProcessor.getScalarTypeName(spec)` would
receive `spec = null`), so an explicit `null -> Unit::class.asClassName()` branch was added
ahead of the `else`, mirroring the sync-response fallback. `createResponseClassDefinition()` and
`createAsyncResponseClassDefinition()` already fell to `else -> null` for any non-`ObjectSpec`
typeSpec, which a null typeSpec also matches, so those needed no change.

Net effect: a `noPayload: true` response/asyncResponse generates the same way an
already-supported scalarless/empty response does — a `Unit`-typed response with no generated
class body.

### HTML doc generation (`htmlgen/ObjectInspectionHelper.kt`, `rest_api_doc.vm`)

Added a `hasNoPayload(response: Response?)` helper and corresponding template branches for both
the sync `Response` and `asyncResponse` sections, rendering "No payload." instead of falling
through to code paths that assumed a non-null `type` (e.g. `$method.response.type.name()`),
which would otherwise hit a null reference once `noPayload: true` responses started appearing
in real docs.

---

## Non-goals (carried over from the original proposal)

- An absent `response`/`asyncResponse` block still implicitly means "no payload," as before —
  this change does not add a warning for the implicit (block-omitted) form. Nudging authors
  toward the explicit `noPayload: true` form via a validator warning is a follow-on, not part of
  this change.
- No change to how `parsedAs`/`type: string` document bare scalars or raw text — those already
  worked and are unaffected.
- `noPayload` was not added to `DataType` and is not usable on nested `Field`s or `object`
  members — it lives on `Response` only.

---

## Verification

`./gradlew compileKotlin compileTestKotlin` and `./gradlew test` both passed after the change,
with no adjustments needed to existing tests.
