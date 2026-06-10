---
phase: 01-storage-model-validation-foundation
plan: 01
subsystem: storage-domain-pipeline
tags: [asyncResponse, storage-model, domain-model, mapper, jackson]
dependency_graph:
  requires: []
  provides: [storage.Method.asyncResponse, domain.Method.asyncResponse, mapper-wiring]
  affects: [doc_generator, kotlin_generator, doc_validator]
tech_stack:
  added: []
  patterns: [nullable-optional-field, safe-call-mapper-delegation]
key_files:
  created: []
  modified:
    - src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt
    - src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt
    - src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt
decisions:
  - asyncResponse reuses existing Response type verbatim (no new types needed)
  - No @JsonProperty annotation needed (YAML key matches Kotlin property name)
  - ResponseStorageModel.mapToModel provides structural validation for free
metrics:
  duration: 6 minutes
  completed: 2026-06-02T23:12:00Z
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 01 Plan 01: Storage + Domain asyncResponse Field Summary

**One-liner:** Added nullable asyncResponse field to storage.Method and domain.Method with mapper wiring via safe-call delegation to existing ResponseStorageModel.mapToModel

## Tasks Completed

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Add asyncResponse field to storage.Method | 9b5ef2f | val asyncResponse: Response? = null in data class constructor |
| 2 | Add asyncResponse to domain.Method and wire mapper | cf16bf8 | var asyncResponse + mapper line asyncResponse?.mapToModel(context) |

## Approach

Both tasks follow the plan exactly. The asyncResponse field mirrors the existing response field in all three locations:

1. **storage.Method** - Jackson can now bind the `asyncResponse` YAML key without throwing UnrecognizedPropertyException (satisfies FOUND-01, FOUND-02)
2. **domain.Method** - Domain model carries the asyncResponse through the pipeline (satisfies FOUND-03)
3. **StorageToDomainMappers** - Safe-call delegation to ResponseStorageModel.mapToModel provides full structural validation identical to response (satisfies VALID-01, VALID-02)

## Deviations from Plan

### Pre-existing Issue (Out of Scope)

**FieldElementListIncludeOnlyTest.kt compilation failure** - This test references `TypeSpec.DataSpec` and `TypeSpec.ObjectSpec` which do not exist in the current codebase (likely should be `TypeSpec.BasicSpec` and `TypeSpec.ObjectSpec` respectively). This failure pre-dates all asyncResponse changes and exists on the base `allego` branch at commit 6b4c76b. The plan's verification criterion states `./gradlew build exits 0` but this cannot be satisfied due to this pre-existing test issue.

**Impact:** `./gradlew compileKotlin` (main source) passes cleanly. `./gradlew compileTestKotlin` fails on unrelated test file. No asyncResponse-related test failures exist.

## Verification

- `./gradlew compileKotlin` exits 0 (main source compiles)
- storage.Method.kt contains `val asyncResponse: Response? = null` after response field
- domain.Method.kt contains `var asyncResponse: Response? = null` after response field
- StorageToDomainMappers.kt contains `asyncResponse = asyncResponse?.mapToModel(context)` after response mapping line
- No @JsonProperty annotation on asyncResponse field (key matches property name)

## Requirements Satisfied

- **FOUND-01**: asyncResponse field parseable from YAML without UnrecognizedPropertyException
- **FOUND-02**: storage.Method declares asyncResponse: Response? = null
- **FOUND-03**: domain.Method declares asyncResponse: Response? = null
- **VALID-01**: asyncResponse mapped through StorageToDomainMappers pipeline
- **VALID-02**: Structural validation via reuse of ResponseStorageModel.mapToModel (same rules as response)

## Known Stubs

None - all fields are fully wired with production types and mapper logic.

## Self-Check: PASSED
