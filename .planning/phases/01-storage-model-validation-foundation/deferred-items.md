# Deferred Items — Phase 01

## Pre-existing Issues (Out of Scope)

### FieldElementListIncludeOnlyTest.kt compilation failure

- **File:** `src/test/kotlin/com/giffardtechnologies/restdocs/domain/FieldElementListIncludeOnlyTest.kt`
- **Issue:** References `TypeSpec.DataSpec` and `TypeSpec.ObjectSpec` which do not exist in the current codebase
- **Impact:** `./gradlew compileTestKotlin` fails; `./gradlew compileKotlin` (main source) passes
- **Discovered during:** Plan 01-01, Task 2 verification
- **Root cause:** Test was written against an API that has since been renamed/refactored (commit 80598f2)
- **Recommendation:** Update test to use correct TypeSpec subtypes (likely `TypeSpec.BasicSpec` for DataSpec)
