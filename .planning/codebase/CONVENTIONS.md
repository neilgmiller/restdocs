# Coding Conventions

**Analysis Date:** 2026-03-27

## Naming Patterns

**Files:**
- Classes: PascalCase (e.g., `DocGenerator.kt`, `EnumProcessor.kt`, `DataObject.kt`)
- Extension functions: Located in files named after the extended type with `Ext` suffix (e.g., `ModelExts.kt`, `ArrayExt.kt`)
- Builders/DSLs: Placed in `domain/dsl/` directory

**Functions:**
- Public functions: camelCase (e.g., `processEnum()`, `generateDataObjectClassFile()`, `toClassNameStyle()`)
- Extension functions: camelCase (e.g., `toClassNameStyle()`, `toPropertyName()`, `toObjectName()`)
- Private functions: camelCase (e.g., `generateHTML()`, `processEnumToTypeSpec()`)

**Variables:**
- Local variables: camelCase (e.g., `mapper`, `document`, `dtoPackage`)
- Constants (in companion objects): UPPER_CASE (e.g., `alphaNumericRegex`, `alphaNumericWithSpacesRegex`)
- Private fields: camelCase with `m` prefix for class members (e.g., `mDocument` in `ModelExts`)

**Types:**
- Data classes: PascalCase (e.g., `Field`, `Document`, `Options`, `NamedEnumeration`)
- Sealed/abstract classes: PascalCase (e.g., `TypeSpec`, `TypeRefSpec`, `ObjectSpec`)
- Enums: PascalCase (e.g., `DataType`, `KeyType`, `FlagType`)

## Code Style

**Formatting:**
- No explicit formatter configuration found (eslintrc, prettierrc, etc. not detected)
- Kotlin standard conventions followed implicitly
- String interpolation used with `${}` syntax (e.g., `println("Template file is: $templateFile")`)

**Linting:**
- No detekt or ktlint configuration found
- Annotations used for Jackson configuration: `@JsonProperty`, `@JsonDeserialize`
- `@Suppress("unused")` used for unused but intentional code (`ModelExts` class)

## Import Organization

**Order:**
1. Standard library imports (`java.*`, `kotlin.*`)
2. Third-party framework imports (`org.apache.*`, `com.squareup.*`, `io.vavr.*`)
3. Jackson imports (`com.fasterxml.jackson.*`, `tools.jackson.*`)
4. Internal project imports (all `com.giffardtechnologies.restdocs.*`)
5. Type aliases for ambiguous imports (e.g., `import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel`)

**Path Aliases:**
- Type aliases are used when same name exists in multiple packages:
  ```kotlin
  import io.vavr.collection.HashSet as VavrHashSet
  import io.vavr.collection.Set as VavrSet
  import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
  ```
- No IDE path aliases detected; imports are explicit

## Error Handling

**Patterns:**
- Throws declarations explicit: `@Throws(IOException::class)`, `@Throws(JacksonException::class)`
- ValidationException used for domain validation failures: `throw ValidationException("Field must have a name")`
- IllegalStateException used for illegal state conditions in domain resolution
- Custom exception types: `ValidationException` in `com.giffardtechnologies.restdocs.jackson.validation` package
- IllegalArgumentException used in generated code paths: `throw IllegalArgumentException("Unsupported value: '$this'")`
- Try-catch blocks wrap I/O operations and exception mapping

**Example:**
```kotlin
override fun validate(validationContext: Any?) {
    super.validate(validationContext)
    if (name.isBlank()) {
        throw ValidationException("Field must have a name")
    }
    if (!longName.matches(getLongNameValidationRegex(validationContext))) {
        throw ValidationException("Field long name must be alphanumeric...")
    }
}
```

## Logging

**Framework:** `println()` and `System.out` only (no dedicated logging framework)

**Patterns:**
- Verbose logging controlled by `Options.verboseLogging` flag
- Console output via `println()` for status/progress messages
- No structured logging or log levels observed
- File I/O operations print their path for transparency:
  ```kotlin
  if (options.verboseLogging) {
      println("Template file is: $templateFile")
  }
  ```
- Messages printed during validation/generation phases:
  ```kotlin
  messageHandler("First pass complete")
  messageHandler("Second pass complete")
  messageHandler("SUCCESS!")
  ```

## Comments

**When to Comment:**
- Comments rare; self-documenting code preferred
- Comments used only in complex regex patterns or multi-step algorithms
- Block comments used for disabled/archived code (not cleaned up)
- Inline comments in rare cases where intent is non-obvious

**KDoc/JSDoc:**
- Extensive KDoc used for public APIs and domain classes
- KDoc format: `/** ... */`
- Includes `@property` tags for primary constructor parameters
- Includes `@param` tags for non-constructor parameters
- Includes clear descriptions of validation behavior and state

**Example:**
```kotlin
/**
 * A typed, named field that can appear in a data object, method parameters, headers, or an inline
 * object's field list.
 *
 * @property name The short (wire) name of the field as it appears in the JSON payload.
 * @property longName A human-readable, alphanumeric name used for code generation.
 * @param type The explicit [DataType] of this field.
 * @param parsedAs Used only when [type] is [DataType.STRING]...
 */
```

## Function Design

**Size:** Functions typically 5-30 lines; larger functions break into private helpers
  - `DocGenerator.generateHTML()`: 28 lines with clear responsibilities
  - `EnumProcessor.processEnumToSealedTypeSpec()`: 90 lines for complex type building (allowable for code generation)
  - Extension functions: 1-10 lines typically

**Parameters:**
- Functions accept 1-5 parameters
- Options pattern used for parameter groups:
  ```kotlin
  data class Options(
      val codeDirectory: File,
      val iOSCodeDirectory: File,
      val clientPackage: String,
      val verboseLogging: Boolean = false,
      val forceTopLevel: Set<FieldReference>,
      val excludedFields: Set<FieldReference>
  )
  ```
- Default parameter values used (e.g., `asInner: Boolean = false`, `useFutureProofEnum: Boolean = true`)

**Return Values:**
- Single return type; no tuple returns observed
- Functions return domain types or Unit
- Builder methods return `TypeSpec`, `FileSpec`, or generated classes
- Extension functions often return transformed versions of input

## Module Design

**Exports:**
- All top-level types exported from their packages
- No barrel files or index exports observed
- Packages have clear responsibility boundaries:
  - `com.giffardtechnologies.restdocs.storage.*` - Jackson-deserialized storage model
  - `com.giffardtechnologies.restdocs.domain.*` - Business domain model
  - `com.giffardtechnologies.restdocs.codegen.*` - Code generation logic
  - `com.giffardtechnologies.restdocs.mappers.*` - Mapping between storage and domain

**Barrel Files:** Not used; imports are explicit to specific classes

## Data Classes

**Immutability:**
- `data class` used extensively for value objects: `Field`, `Document`, `Options`, `DataObject`
- Primary constructor parameters become properties automatically
- Copy methods explicitly defined when needed (e.g., `Field.copy(isRequired: Boolean)`)
- Vavr immutable collections used where mutability is undesirable: `io.vavr.collection.Array<T>`

**Example:**
```kotlin
data class Field(
    val name: String,
    val longName: String,
    val type: TypeSpec,
    val description: String? = null,
    val defaultValue: String? = null,
    val isRequired: Boolean = true,
    val sampleValues: Array<String> = Array.empty(),
    val parentName: String? = null,
) : FieldListElement
```

## Interfaces and Inheritance

**Pattern:**
- `Validatable` marker interface for types requiring validation
- `FieldListElement` interface for union-like behavior (objects or include elements)
- Sealed classes used for TypeSpec hierarchy: `TypeSpec.ObjectSpec`, `TypeSpec.ArraySpec`, `TypeSpec.EnumSpec`
- Inheritance used sparingly; composition and interfaces preferred

---

*Convention analysis: 2026-03-27*
