# CONCERNS

## Critical / Incomplete Implementations

### Incomplete `interpretedAs` handling
- **Location**: `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` ~line 236
- **Issue**: `interpretedAs` is not mapped for `FLOAT`, `DOUBLE`, and `STRING` data types — only partially implemented
- **Risk**: Fields with `parsedAs`/`interpretedAs` on these types will silently drop the annotation during storage→domain mapping

### Numeric enum default values not converted to names
- **Location**: `src/main/java/com/giffardtechnologies/restdocs/codegen/FieldAndTypeProcessor.kt` ~line 102
- **Issue**: When an enum field has a numeric default value, it is not converted to the corresponding enum constant name in generated code
- **Risk**: Generated Kotlin code may contain raw integer defaults instead of named enum constants

### Non-JSON responses not supported
- The pipeline assumes all API responses are JSON. Non-JSON (e.g., binary, plain text, multipart) response types have no handling path.
- **Risk**: Any API method returning non-JSON will either fail or be silently ignored during doc/code generation

### Unimplemented `TODO()` in method deserialization
- At least one `TODO()` call exists in the method deserialization path — will throw `NotImplementedError` at runtime if the code path is triggered

---

## Code Quality / Tech Debt

### MethodProcessor.kt is excessively large
- **Location**: `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt` (742 lines)
- Lines 454–741 contain extensively commented-out code
- **Risk**: Hard to maintain; commented code creates noise and ambiguity about what is active

### Duplicate validation logic
- `src/main/java/com/giffardtechnologies/restdocs/domain/dsl/EnumSpec.kt` and `src/main/java/com/giffardtechnologies/restdocs/domain/dsl/BitSetSpec.kt` contain duplicated validation logic
- **Risk**: Bug fixes or rule changes must be applied in multiple places

### FieldElementList complexity
- `src/main/java/com/giffardtechnologies/restdocs/domain/FieldElementList.kt` (223 lines) manages field resolution with `includeOnly`/`excluding` path logic
- The storage counterpart `src/main/java/com/giffardtechnologies/restdocs/storage/type/FieldElementList.kt` has parallel but divergent logic
- **Risk**: Two similar implementations evolving independently

### Method ID mapping via `when` blocks
- Method type/ID mappings use fragile `when` expressions — adding a new method type requires updating multiple locations
- **Risk**: Easy to forget one branch when extending the system

---

## Testing Gaps

- **Only 2 test files** (~750 lines) for ~6K+ lines of production code
- `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` (488 lines) — **no tests**
- `src/main/java/com/giffardtechnologies/restdocs/codegen/FieldAndTypeProcessor.kt` (312 lines) — **no tests**
- `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt` (742 lines) — **no tests**
- No integration/end-to-end tests: no test exercises the full YAML → domain → generated output pipeline
- **Risk**: Regressions in code generation or mapping logic will not be caught automatically

---

## Dependency Risk

### Jackson 3.x alpha (`tools.jackson` group)
- **Dependency**: `tools.jackson:jackson-databind:3.1.0` (Jackson 3.x, alpha-stage group ID)
- Jackson 3.x is not a stable GA release; the `tools.jackson` group ID is the pre-release incubation namespace
- **Risk**: API instability, potential breaking changes, limited community support compared to `com.fasterxml.jackson` 2.x

### `futureproofenum` 1.0-SNAPSHOT
- **Dependency**: Internal Allego library, version `1.0-SNAPSHOT`, sourced from local ivy repo (`~/.ivy/repo`) or Allego Artifactory
- SNAPSHOT versioning means the resolved artifact can change without a version bump
- **Location**: `manual-libs/` for local fallback, Artifactory for CI
- **Risk**: Non-reproducible builds; behavior may silently change between builds if the SNAPSHOT is updated

---

## Infrastructure / Configuration

### Source directory convention mismatch
- Kotlin files live under `src/main/java/` (Java directory convention) rather than `src/main/kotlin/`
- Works but is non-standard for a pure Kotlin project; may confuse tooling or onboarders

### Bundled Velocity tools patch
- `src/main/java/org/apache/velocity/tools/view/` contains a patched copy of Apache Velocity tools bundled directly in source
- **Risk**: Updates to the upstream Velocity library won't automatically apply; divergence will grow over time
