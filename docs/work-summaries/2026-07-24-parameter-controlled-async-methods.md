# Parameter-Controlled Sync/Async Response Methods — Commit 9080e3e

**Branch:** `allego`
**Date:** 2026-07-24
**Files changed:** 19 (+1087 / −265)

---

## Overview

Some Allego API methods take a boolean parameter that lets the caller choose, per-request,
whether the server returns the payload directly or an async job ID to poll later. The existing
async model (`Method.response` / `Method.asyncResponse`, with async-ness auto-detected from a
`job` field per `DocValidator.ValidationContext.responseIsAsync`) could only express "always
sync" or "always async" for a given method — there was no way to say "this one shape-shifts at
runtime based on a parameter." This work adds that third mode and threads it end to end through
the storage model, validator, domain model, codegen, and HTML doc template.

The implementation ended up diverging substantially from the plan file committed alongside it
(`docs/plans/parameter-controlled-async-methods.md`) — see **Deviations from the plan** below —
which is why that file is being removed rather than kept as a record of what shipped.

---

## What Was Built

### Explicit `asyncMode` replaces auto-detection (`storage/Method.kt`, `DocValidator.kt`)

The old model inferred "this method is async" by inspecting `response` for a `job` field
(`responseIsAsync`) and warned if `asyncResponse` was missing. That inference approach couldn't
support a third, runtime-conditional case, so it was removed outright: `responseIsAsync`,
`ValidationOptions.asyncCapableResponseTypes`, and the VALID-03/VALID-04 warnings are gone.

In its place, `Method` gained an explicit `asyncMode: AsyncMode?` field (`ALWAYS` or
`CONDITIONAL`, `null` meaning plain synchronous) and `response` was split into two independent
fields:
- `jobResponse` — the job envelope returned immediately by an async call (renamed from
  `asyncResponse`... in reverse — see below).
- `payloadResponse` — the real data payload, returned directly by a sync call or obtained by
  polling after `jobResponse` comes back.

`response` (plain sync) still exists for ordinary methods and is now mutually exclusive with
`jobResponse`/`payloadResponse`.

### `AsyncMode.CONDITIONAL` and the control parameter (`storage/Method.kt`, `storage/Service.kt`)

- `Common.asyncControlParameterName` (service-wide default) and `Method.asyncControlParameter`
  (per-method override) name the boolean parameter that switches a `CONDITIONAL` method between
  shapes. `Method.resolveAsyncControlParameterName`/`resolveAsyncControlParameterField` resolve
  the effective name/field (override-or-default), reused by validation, codegen, and doc
  generation.
- Validation in `Method.validateAccumulated()` now enforces, per `asyncMode`:
  - `response` can never coexist with `jobResponse`/`payloadResponse`.
  - `asyncMode` is required whenever either async field is set, and forbidden otherwise.
  - `asyncControlParameter` may only be set when `asyncMode == CONDITIONAL`.
  - `CONDITIONAL` requires a resolved control parameter name that matches an actual boolean
    parameter (by `DataType.BOOLEAN` or `interpretedAs`), and requires both `jobResponse` and
    `payloadResponse` to be present.
  - `ALWAYS` requires both `jobResponse` and `payloadResponse` to be present (symmetric check,
    each direction reported separately).

### Codegen: `executeSync*`/`executeAsync*` instead of a single `execute*` (`MethodProcessor.kt`, `KotlinGenerator.kt`)

`MethodProcessor.getClassNames()` now resolves whether a method has a control parameter and
returns `MethodClassNames.hasAsyncControlParameter`, plus two derived properties,
`asyncRequestClassName`/`syncRequestClassName`, that point at nested `Async`/`Sync` classes
instead of a single request class.

For a `CONDITIONAL` method, `processMethod()` generates the request class as a non-constructible
container holding two nested request classes (`<Method>Request.Async` and
`<Method>Request.Sync`), each built from the same `Params` definition but with the control field
force-set to `true`/`false` respectively and dropped from the generated public constructor
entirely — callers never see or set it.

`KotlinGenerator` mirrors this on the call-site: methods without a control parameter still emit
a single `execute`/`executeBlocking`/`executeForResult` trio exactly as before. Methods with one
instead emit two trios — `executeAsync*` (takes the `Async` request, returns the job response
type) and `executeSync*` (takes the `Sync` request, returns the payload type) — sharing the same
underlying `apiServerClient.execute()` call and `@Throws` annotation logic via an
`ExecuteVariant` list, rather than duplicating the function-building code per variant.

### HTML docs (`htmlgen/ObjectInspectionHelper.kt`, `rest_api_doc.vm`)

Added `hasAsyncControlParameter`, `isAlwaysAsync`, `isAsyncMethod`, and
`asyncControlParameterName` helpers. The method template now renders a blue "SYNC/ASYNC" badge
(conditional) or green "ASYNC" badge (always) next to the existing red "DEPRECATED" badge, plus
an explanatory sentence naming the control parameter. The response section branches three ways
instead of two: plain sync (`response`), always-async (`jobResponse` + `payloadResponse`, no
mention of a switch), and conditional (`jobResponse` + `payloadResponse`, with sync/async framed
as caller-chosen). `asyncResponseAsField` was renamed `responseAsField` since it's now shared by
both response kinds.

### Domain model, mapper, classifier (`domain/Method.kt`, `StorageToDomainMappers.kt`, `DataObjectUsageClassifier.kt`)

Mechanical mirroring of the storage-layer renames/additions: `domain.Method` gained its own
`AsyncMode` enum, `asyncMode`, `asyncControlParameter`, `payloadResponse`, `jobResponse`
(replacing `asyncResponse`); the mapper carries all of it through plus
`Common.asyncControlParameterName`; `DataObjectUsageClassifier` walks both `jobResponse` and
`payloadResponse` for data-object usage instead of just `asyncResponse`.

### Tests

- `AsyncResponseSmokeTest.kt` (new) and `MethodProcessorAsyncResponseTest.kt` (updated) exercise
  codegen output for `ALWAYS` and `CONDITIONAL` methods.
- `MethodValidationTest.kt` (new) covers the validation matrix above — missing `asyncMode`,
  mismatched field presence, non-boolean control parameter, control parameter set without
  `CONDITIONAL`, etc.
- `DocGeneratorAsyncHtmlTest.kt` and `DataObjectUsageClassifierTest.kt` updated for the
  `jobResponse`/`payloadResponse` split.
- `async-response-smoke-test.yaml` extended with fixtures for both async modes.

---

## Deviations from the plan

The committed plan (`docs/plans/parameter-controlled-async-methods.md`) described reusing
`response`/`asyncResponse` unchanged and adding only the control-parameter fields on top. What
shipped instead:

- Removed the old `response`-has-a-`job`-field auto-detection (`responseIsAsync`,
  `asyncCapableResponseTypes`) entirely, rather than layering the new behavior over it.
- Introduced an explicit `AsyncMode` enum (`ALWAYS`/`CONDITIONAL`) as the source of truth for
  whether/how a method is async, rather than inferring it from which fields are non-null.
- Renamed `asyncResponse` → `jobResponse` and split what the plan called `response`-reused-for-
  the-async-payload into its own `payloadResponse` field, rather than reusing `response` as-is
  for the payload shape in the async case.
- As a result, a plain synchronous method still uses `response`, but any async method (always or
  conditional) uses `jobResponse`/`payloadResponse` instead — `response` and the async fields are
  now mutually exclusive, which the plan didn't anticipate.

The plan file was removed as part of this summary since it no longer reflects the shipped
design and would be misleading if left in place.

---

## Verification

`./gradlew test` passes, covering the new validation matrix and codegen output for both async
modes.
