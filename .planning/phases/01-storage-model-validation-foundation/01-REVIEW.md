---
phase: 01-storage-model-validation-foundation
reviewed: 2026-06-02T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt
  - src/main/java/com/giffardtechnologies/restdocs/domain/Method.kt
  - src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt
  - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt
  - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt
  - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt
  - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt
  - src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt
  - src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt
  - src/main/java/com/giffardtechnologies/restdocs/storage/NamedBitSet.kt
  - src/main/java/com/giffardtechnologies/restdocs/storage/NamedEnumeration.kt
  - src/main/java/com/giffardtechnologies/restdocs/storage/type/Field.kt
findings:
  critical: 3
  critical_resolved: 3
  warning: 5
  warning_resolved: 5
  info: 3
  info_resolved: 3
  total: 11
  resolved: 11
status: all_resolved
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-02
**Depth:** standard
**Files Reviewed:** 12
**Status:** All criticals and warnings resolved

## Summary

This review covers the Phase 1 implementation of the `asyncResponse` storage model, validation
logic, and domain mapper. The implementation adds `asyncResponse` as a new optional sibling field
to `response` in both the storage and domain `Method` models, plus cross-field validation rules
(VALID-03, VALID-04) that fire during the second validation pass.

Three blockers were found. Two are logic errors in the existing validation infrastructure that
were either introduced or exposed by the new code: (1) a `longName` duplicate-detection bug in
`TypeSpec` that tracks `it.value` instead of `it.longName` in the seen-names set, silently
allowing duplicate long names in inline enums; (2) `deserializeWithType` and the merging
`deserialize` overload skip the `Validatable` check, so polymorphically-deserialised objects
are never validated; and (3) header fields are unconditionally mapped to `DataType.IntType`
regardless of their declared type, corrupting the domain model for every header. All three
blockers and all five warnings have since been fixed.

## Critical Issues

### CR-01: Enum `longName` duplicate set adds the wrong value ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/type/TypeSpec.kt`
**Resolution:** Changed `valuesNameSet.add(it.value)` to `valuesNameSet.add(it.longName)` so the
duplicate-longName check actually fires. Commit `06b1781`.

---

### CR-02: `deserializeWithType` bypasses `Validatable` check ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt`
**Resolution:** Added `validate()` calls to both the `deserializeWithType` override and the
merge-into-existing-value `deserialize` overload. Polymorphically-deserialized and merged objects
now go through the full validation pipeline. Commit `cdd1d9c`.

---

### CR-03: All header fields mapped to `DataType.IntType` regardless of declared type ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
**Resolution:** `mapToModelInHeaderContext()` now delegates to the standard type mapper instead of
hardcoding `TypeSpec.BasicSpec(DataType.IntType)`. Commit `1bb5574`.

---

## Warnings

### WR-01: Async sentinel type names hard-coded in `Method.validate()` ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/Method.kt`
**Resolution:** Sentinel names (`AsyncJobResponse`, `AsyncCapableResponse`) moved out of the
validation method into `ValidationOptions.asyncCapableResponseTypes`. Detection logic extracted
to `ValidationContext.responseIsAsync(Response)` so callers can configure the names without
touching validation logic. Commit `381c5da`.

---

### WR-02: `TODO()` calls in `convertToTypeSpec` escape the exception guard ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
**Resolution:** `BasicType.FLOAT`, `BasicType.DOUBLE`, and `BasicType.STRING` branches now throw
`ValidationException` with a descriptive "unsupported interpretedAs" message instead of
`NotImplementedError`. Commit `e5968e9`.

---

### WR-03: `KeyType.ENUM` collection mapping throws `TODO()` ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
**Resolution:** `KeyType.ENUM` in the `COLLECTION` branch now throws
`ValidationException("... cannot use 'enum' as a key type")` instead of `NotImplementedError`.
Commit `e5968e9`.

---

### WR-04: Second YAML parse discards its result silently ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt`
**Resolution:** Documented at the call site that the second parse is run for validation
side-effects only; since validation does not mutate storage models both passes produce identical
documents, so discarding the second result is intentional and not a correctness bug. Commit `0ac8444`.

---

### WR-05: `NamedBitSet` / `NamedEnumeration` key-type constraints only enforced in mapper ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/type/TypeSpec.kt`
**Resolution:** Added a `STRING`/`ENUM` key-type guard to the `DataType.BITSET` branch in
`TypeSpec.validate()`, mirroring the existing `DataType.ENUM` guard. The constraint is now caught
at the storage layer with a `ValidationException` rather than surfacing as an opaque
`IllegalArgumentException` from the mapper. Commit `c13c627`.

---

## Info

### IN-01: `parentType` field in `FieldElementList` (storage) is set but never read ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/type/FieldElementList.kt`
**Resolution:** Removed the `parentType` field, `setParentType()` setter, and its KDoc. Confirmed
no callers of `setParentType` existed anywhere in the codebase.

---

### IN-02: `hasFields()` method and `hasFields` property are duplicate API on `FieldElementList` ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/storage/type/FieldElementList.kt`
**Resolution:** Removed `fun hasFields(): Boolean`, kept `val hasFields: Boolean`. No external
callers existed for either form.

---

### IN-03: Several `TODO` comments remain in production code ✓ RESOLVED

**File:** `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
**Resolution:** Converted all three TODOs to tracked issues in `ISSUES.md` at the project root
(sample values mapping, STRING→BOOLEAN spec validation, response type-spec ID).

---

_Reviewed: 2026-06-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
