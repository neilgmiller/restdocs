---
phase: 01-storage-model-validation-foundation
plan: 02
subsystem: jackson-validation
tags: [warningEmitter, validation, jackson, infrastructure]
dependency_graph:
  requires: []
  provides: [warningEmitter-channel, validatable-2arg]
  affects: [doc_validator, kotlin_generator, doc_generator]
tech_stack:
  added: []
  patterns: [function-type-threading, kotlin-default-interface-methods]
key_files:
  created: []
  modified:
    - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/Validatable.kt
    - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidationModule.kt
    - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingBeanDeserializerModifier.kt
    - src/main/java/com/giffardtechnologies/restdocs/jackson/validation/ValidatingDeserializer.kt
    - src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt
    - src/main/java/com/giffardtechnologies/restdocs/storage/NamedBitSet.kt
    - src/main/java/com/giffardtechnologies/restdocs/storage/NamedEnumeration.kt
    - src/main/java/com/giffardtechnologies/restdocs/storage/type/Field.kt
decisions:
  - "warningEmitter uses (String) -> Unit function type with no-op default at all public API boundaries"
  - "ValidatingDeserializer gets default = {} on constructor param for backward compat during incremental build"
metrics:
  duration: 218s
  completed: "2026-06-02T23:17:49Z"
---

# Phase 01 Plan 02: warningEmitter Channel Threading Summary

**One-liner:** Thread (String)->Unit warningEmitter from createMapper through ValidationModule, ValidatingBeanDeserializerModifier, and all three ValidatingDeserializer factory sites to the 2-arg validate() overload.

## Tasks Completed

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Extend Validatable + thread through ValidationModule/Modifier | 21ec39b | 2-arg validate default method, warningEmitter in Module/Modifier constructors, super<TypeSpec> disambiguation |
| 2 | Thread through ValidatingDeserializer + createMapper | db5696f | All 3 factory sites updated, deserialize calls 2-arg validate, createMapper accepts warningEmitter |

## Verification Results

- `./gradlew compileKotlin` exits 0 (main source compiles cleanly)
- All three ValidatingDeserializer constructor calls include warningEmitter as third argument
- Full chain verified: createMapper -> ValidationModule -> ValidatingBeanDeserializerModifier -> ValidatingDeserializer -> validate(ctx, emitter)
- Pre-existing test compilation failure (unresolved `DataSpec` reference in FieldElementListIncludeOnlyTest.kt) is NOT caused by these changes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ValidatingDeserializer constructor parameter added in Task 1**
- **Found during:** Task 1
- **Issue:** Plan has Task 1 modifying createDelegate to pass 3 args to ValidatingDeserializer, but the constructor parameter was planned for Task 2. Compilation would fail without it.
- **Fix:** Added warningEmitter parameter with `= {}` default to ValidatingDeserializer constructor in Task 1.
- **Files modified:** ValidatingDeserializer.kt
- **Commit:** 21ec39b

**2. [Rule 3 - Blocking] Kotlin super.validate() diamond ambiguity**
- **Found during:** Task 1
- **Issue:** NamedBitSet, NamedEnumeration, and Field all extend TypeSpec (which implements Validatable) AND directly implement Validatable. Adding a default method to Validatable created diamond-inheritance ambiguity for `super.validate(validationContext)` calls.
- **Fix:** Changed to `super<TypeSpec>.validate(validationContext)` in all three files.
- **Files modified:** NamedBitSet.kt, NamedEnumeration.kt, Field.kt
- **Commit:** 21ec39b

## Pre-existing Issues (Out of Scope)

- `src/test/kotlin/.../domain/FieldElementListIncludeOnlyTest.kt` has unresolved `DataSpec` reference causing `compileTestKotlin` to fail. This test file was last modified in commit 80598f2 (predates this plan). Not caused by warningEmitter changes. Logged to deferred-items.md if not already tracked.

## Success Criteria Verification

- [x] Validatable interface has 2-arg validate overload with default body
- [x] createMapper accepts warningEmitter: (String) -> Unit = {} as second parameter
- [x] Full chain: createMapper -> ValidationModule -> ValidatingBeanDeserializerModifier -> ValidatingDeserializer -> validate() all thread warningEmitter
- [x] All three ValidatingDeserializer factory sites include warningEmitter
- [x] ./gradlew compileKotlin passes with no new failures

## Self-Check: PASSED

All modified files exist. Both task commits (21ec39b, db5696f) verified in git log.
