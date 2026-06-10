---
phase: 03-html-rendering
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - rest_api_doc.vm
  - src/test/kotlin/com/giffardtechnologies/restdocs/htmlgen/DocGeneratorAsyncHtmlTest.kt
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: resolved
---

# Phase 03: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Two files were reviewed: the Velocity HTML template (`rest_api_doc.vm`) and the new async HTML smoke-test (`DocGeneratorAsyncHtmlTest.kt`). The async response rendering block added to the template is structurally correct for the inline-fields case. CR-01 (broken `requestBody.encoding` reference), WR-01 (Response object passed as String), and WR-02 (description rendered twice) are all resolved by replacing the template with the canonical live template from `allego1.0/doc/api-docs/rest_api_doc.vm` and grafting the `asyncResponse` block onto it. Remaining open issues: the `asyncResponse` typeRef branch is untested (WR-03); and two minor test quality issues (IN-01, IN-02).

---

## Critical Issues

### CR-01: `$resource.requestBody.encoding` references a non-existent property ✓ FIXED

**File:** `rest_api_doc.vm:152`
**Issue:** `RequestBody` has no `encoding` field (only `description` and `contentTypes`). Velocity resolves missing properties by rendering the raw literal `$resource.requestBody.encoding` (or empty string, depending on `runtime.references.strict`). Either way, the output is wrong for every method that has a request body.
**Fix applied:** Removed the `<p style="font-weight: bold">$resource.requestBody.encoding</p>` line. Request body section now renders only the description, matching the description-only pattern.

---

## Warnings

### WR-01: `$link.type($resource.response)` passes a `Response` object, not a String ✓ RESOLVED

Resolved by replacing the template with the canonical live template. The live template uses `$helper.isTypeRef($method.response)` / `$method.response.typeRef` / `$helper.getFields($method.response)` for correct structured response rendering.

### WR-02: Resource description rendered twice per method ✓ RESOLVED

Resolved by replacing the template with the canonical live template. The live template has a single description call per method.

### WR-03: `asyncResponse` typeRef branch is untested ✓ FIXED

**File:** `rest_api_doc.vm:409-410`
**Fix applied:** Added `typeRefAsyncMethod` (case 4) to `async-response-smoke-test.yaml` with `asyncResponse: { typeref: AsyncJobResponse }`. Added a section-bounded assertion confirming the typeRef link renders inside the async section.

### WR-04: `getEffectiveFields` has wrong return type and is dead code ✓ FIXED

**File:** `src/main/java/com/giffardtechnologies/restdocs/htmlgen/ObjectInspectionHelper.kt:71`
**Issue:** `fun getEffectiveFields(typeSpec: TypeSpec): Boolean` returns `Boolean` but is named like a getter that should return a collection. It is also never called from any template or production code. The method body `return !typeSpec.fields.isNullOrEmpty()` is identical to `hasFields(typeSpec: TypeSpec)` at line 63, making it a duplicate predicate with a misleading name.
**Fix applied:** Deleted `getEffectiveFields`. Build passes.

---

## Info

### IN-01: Unused import `assertFalse` ✓ FIXED

**File:** `DocGeneratorAsyncHtmlTest.kt`
**Fix applied:** Removed the unused `assertFalse` import (re-added when IN-02 fix required it).

### IN-02: "Does not appear for sync method" assertion tests a global count, not method-local absence ✓ FIXED

**File:** `DocGeneratorAsyncHtmlTest.kt`
**Fix applied:** Replaced the fragile count-based check with a section-bounded assertion using `substringAfter(id="syncMethod")` + `substringBefore("<h4 ")`, which scopes the check to exactly syncMethod's section and remains correct if new methods are added.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
