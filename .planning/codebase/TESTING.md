# TESTING

## Framework & Runtime

- **Test framework**: JUnit 5 (Jupiter) — `org.junit.jupiter.api.Test`, `Assertions.*`
- **Test language**: Kotlin
- **Build integration**: Gradle `test` task
- **Assertions**: JUnit 5 `assertEquals`, `assertThrows` (no third-party assertion library)
- **No mocking framework** detected — tests use hand-rolled fakes (anonymous objects implementing interfaces)

## Test Structure

```
src/test/kotlin/com/giffardtechnologies/restdocs/
├── domain/
│   └── FieldElementListIncludeOnlyTest.kt    # Domain-layer includeOnly/excluding behavior
└── storage/type/
    └── FieldListIncludeElementTest.kt         # Storage-layer includeOnly/excluding + validation
```

Only 2 test files exist. The test suite is minimal relative to production code size (~750 lines tests vs ~6K+ lines production).

## Test Patterns

### Builder Helpers
Both test files use private helper functions to build test fixtures inline:

```kotlin
// Domain test pattern
private fun field(name: String, type: TypeSpec = TypeSpec.DataSpec(DataType.StringType)) =
    Field(name = name, longName = name, type = type)

private fun dataObject(name: String, vararg fields: Field): DataObject {
    val fieldList = FieldElementList(Array.ofAll(fields.toList<FieldListElement>()))
    return DataObject(typeName = name, type = TypeSpec.ObjectSpec(fieldList))
}
```

```kotlin
// Storage test pattern (uses mutable Java collections)
private fun field(name: String, type: DataType) =
    Field(name = name, longName = name, type = type)

private fun document(vararg dataObjects: DataObject) =
    Document(title = "Test", service = null, dataObjects = ArrayList(dataObjects.toList()))
```

### Test Naming
Backtick-style descriptive test names:
```kotlin
@Test
fun `includeOnly and excluding combined narrows then trims`() { ... }
```

### Context/Fake Pattern
Tests requiring type resolution use anonymous objects implementing the `Context` interface:
```kotlin
val context = object : Context {
    override fun getTypeByName(name: String) = if (name == "Profile") profileObject else null
}
```

### Exception Testing
Uses `assertThrows` (JUnit 5 style):
```kotlin
assertThrows(IllegalStateException::class.java) {
    FieldElementList(Array.of(element)).fields
}
```

Storage tests also assert `ValidationException` from the Jackson validation layer:
```kotlin
assertThrows(ValidationException::class.java) {
    element.validate(fullContext(doc))
}
```

## Coverage Summary

| Layer | Key Files | Test Coverage |
|-------|-----------|---------------|
| `domain/` | `FieldElementList.kt`, `FieldListIncludeElement.kt` | Covered (`FieldElementListIncludeOnlyTest`) |
| `storage/type/` | `FieldListIncludeElement.kt`, `FieldElementList.kt` | Covered (`FieldListIncludeElementTest`) |
| `mappers/` | `StorageToDomainMappers.kt` (488 lines) | **No tests** |
| `codegen/` | `FieldAndTypeProcessor.kt` (312 lines), `MethodProcessor.kt` (742 lines) | **No tests** |
| `jackson/` | `JacksonMapperBuilder.kt`, deserializers | **No tests** |
| `htmlgen/` | All files | **No tests** |
| CLI commands | `DocGeneratorCommand`, `KotlinGeneratorCommand` | **No tests** |

## Testing Gaps

1. **No integration tests** — no test exercises the full pipeline (YAML → storage → domain → generated output)
2. **No code generation tests** — `MethodProcessor.kt` (742 lines) and `FieldAndTypeProcessor.kt` (312 lines) are completely untested
3. **No mapper tests** — `StorageToDomainMappers.kt` (488 lines) has no dedicated tests
4. **No CLI smoke tests** — the three command entry points are untested
5. **Parallel test coverage**: Both `storage` and `domain` layers have tests for `FieldListIncludeElement` — these mirror each other and test the same feature at different abstraction levels

## Running Tests

```bash
./gradlew test
```
