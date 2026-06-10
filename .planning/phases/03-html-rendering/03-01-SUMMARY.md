---
phase: "03"
plan: "01"
subsystem: html-rendering
tags: [velocity-template, html-generation, async-response, integration-test]
dependency_graph:
  requires: [storage.Method.asyncResponse (Phase 01)]
  provides: [HTML rendering of asyncResponse section for all methods]
  affects: [rest_api_doc.vm, DocGeneratorAsyncHtmlTest]
tech_stack:
  added: []
  patterns: [Velocity #if/#foreach macros, JUnit @TestInstance(PER_CLASS) with manual tempdir]
key_files:
  created:
    - src/test/kotlin/com/giffardtechnologies/restdocs/htmlgen/DocGeneratorAsyncHtmlTest.kt
    - .planning/phases/03-html-rendering/03-01-PLAN.md
  modified:
    - rest_api_doc.vm
decisions:
  - Template iterates Service.methods directly as Method objects (flat list, not Resource-grouped)
  - Used Files.createTempDirectory instead of @TempDir to avoid lateinit initialization order issue with @TestInstance(PER_CLASS) + @BeforeAll
metrics:
  duration: "~13 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  files_changed: 3
---

# Phase 3 Plan 1: Render asyncResponse Section in rest_api_doc.vm Summary

**One-liner:** Velocity template updated to render "Async Response (via getAsyncJobStatus)" section with field table and prose note for methods with asyncResponse, with end-to-end DocGeneratorAsyncHtmlTest covering all three spec cases.

## What Was Built

### Task 1 — Modify `rest_api_doc.vm`

Added the `#if ($resource.asyncResponse)` block to the methods section of `rest_api_doc.vm`. The block renders:
- `<h5>Async Response (via getAsyncJobStatus)</h5>` heading
- Prose note referencing `jr` field and `jobStatus` value `0`
- Field table using `#fieldheaders` + `#fieldrow` macros for inline-object asyncResponse
- TypeRef link via `$link.typeSimple()` for typeref-shape asyncResponse (elseif branch)

### Task 2 — Write `DocGeneratorAsyncHtmlTest`

Created end-to-end integration test at `src/test/kotlin/com/giffardtechnologies/restdocs/htmlgen/DocGeneratorAsyncHtmlTest.kt`.

Drives `DocGenerator(false).generate()` with `async-response-smoke-test.yaml` and the production `rest_api_doc.vm` template. Five tests:
- (a) Async section heading appears
- (b) Prose note contains `jr` and `jobStatus`
- (c) pureAsyncMethod fields `resultUrl` and `completedAt` appear
- (d) mixedAsyncMethod field `analysisResult` appears
- (e) Regression: exactly 2 async sections (not 3)

### Task 3 — `./gradlew build`

All 49 + 5 = 54 tests pass. Build successful.

## Commits

| Hash | Description |
|------|-------------|
| c9ab055 | feat(03-01): render asyncResponse section in rest_api_doc.vm |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed non-functional double-loop structure in rest_api_doc.vm methods section**

- **Found during:** Task 3 (all 5 new tests failed with empty method sections)
- **Issue:** The template iterated `$document.service.methods` as `$resource` then `$resource.actions` as `$method`. However `Service.methods` is `ArrayList<Method>` and `Method` has no `actions` property — the inner loop never executed, producing empty method sections in HTML output. This was pre-existing broken behavior.
- **Root cause:** The template was written for a two-level Resource/Method hierarchy (`Resource` with `uri` and `actions`), but the storage model uses a flat `ArrayList<Method>`. The plan's research doc noted this as a known gotcha (`$method.methodString` and `$method.hasParameters` not resolving) but didn't identify the `$resource.actions` loop as the primary blocker.
- **Fix:** Removed the outer Resource loop and inner `$resource.actions` loop. The methods section now iterates `$document.service.methods` directly with `$resource` as the Method variable. The async response `#if` block uses `$resource.asyncResponse` accordingly.
- **Files modified:** `rest_api_doc.vm`
- **Commit:** c9ab055

**2. [Rule 1 - Bug] Fixed `@TempDir` lateinit initialization with `@TestInstance(PER_CLASS)` + `@BeforeAll`**

- **Found during:** Task 3 (first build attempt — `UninitializedPropertyAccessException` on `tempDir`)
- **Issue:** With `@TestInstance(PER_CLASS)`, JUnit 5 injects `@TempDir` fields before `@BeforeEach` but not before `@BeforeAll`. The plan's test code used a `@TempDir lateinit var tempDir: File` field accessed from `@BeforeAll`.
- **Fix:** Replaced `@TempDir` field injection with `Files.createTempDirectory()` called directly in `@BeforeAll`.
- **Files modified:** `DocGeneratorAsyncHtmlTest.kt`
- **Commit:** c9ab055

## Known Stubs

None — all assertions test real rendered content.

## Threat Flags

None — template change and test only; no new network endpoints, auth paths, or schema changes.

## Self-Check

- [x] `rest_api_doc.vm` modified and verified in commit c9ab055
- [x] `DocGeneratorAsyncHtmlTest.kt` created and verified in commit c9ab055
- [x] All 5 new tests pass (TEST-DocGeneratorAsyncHtmlTest.xml: tests=5, failures=0, errors=0)
- [x] Full build passes (`./gradlew build` → BUILD SUCCESSFUL)
