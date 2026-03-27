# Architecture

**Analysis Date:** 2026-03-27

## Pattern Overview

**Overall:** Multi-layered transformation pipeline with Storage-Domain-Codegen separation

**Key Characteristics:**
- **Three distinct data models**: Storage model (YAML-deserializable), Domain model (enhanced with type relationships), Codegen model (KotlinPoet AST)
- **Plugin-style command architecture**: CLI entry points via PicoCLI for three independent generators (HTML docs, Kotlin code, Validation)
- **Mapper-based transformation**: Explicit storage→domain mapping layer using DSL builders to ensure type safety and validation
- **Type-driven code generation**: Recursive type processors handle primitives, enums, bitsets, objects, arrays, and maps

## Layers

**Storage/Model Layer:**
- Purpose: Deserialization and representation of YAML input; raw API specification format
- Location: `src/main/java/com/giffardtechnologies/restdocs/storage/`
- Contains: Data classes annotated with Jackson Jackson annotations (Document, Service, Method, DataObject, Field, etc.)
- Depends on: Jackson 3.x for YAML deserialization
- Used by: DocValidator, all mappers

**Domain/Model Layer:**
- Purpose: Type-safe, validated representation with context and type resolution; serves as single source of truth for generation
- Location: `src/main/java/com/giffardtechnologies/restdocs/domain/`
- Contains: Data classes and sealed types for Field, TypeSpec (variants: DataSpec, ObjectSpec, ArraySpec, MapSpec, EnumSpec, BitSetSpec), Method, Document, Service
- Depends on: Vavr (immutable collections), Storage model via mappers
- Used by: Codegen processors, HTML template context

**Mappers/Transformation Layer:**
- Purpose: Convert storage→domain with validation, type resolution, and DSL-based construction
- Location: `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt`
- Contains: Extension functions on storage types that construct domain types using DSL builders (field(), objectSpec(), namedEnumeration(), etc.)
- Depends on: Both storage and domain models
- Used by: DocValidator, KotlinGenerator

**Codegen/Generation Layer:**
- Purpose: Transform domain model into KotlinPoet TypeSpec for file output
- Location: `src/main/java/com/giffardtechnologies/restdocs/codegen/`
- Contains: Processors for each domain type (DataObjectProcessor, MethodProcessor, EnumProcessor, BitSetProcessor, FieldAndTypeProcessor, ObjectProcessor)
- Depends on: Domain model, KotlinPoet, Meter library for file DSL
- Used by: KotlinGenerator

**Output/Documentation Layer:**
- Purpose: Generate HTML documentation or validation reports
- Location: `src/main/java/com/giffardtechnologies/restdocs/htmlgen/`
- Contains: Velocity template tools (LinkTool, PlainTextTool, ObjectInspectionHelper) and template context setup
- Depends on: Domain model, Velocity Engine, Escape utilities
- Used by: DocGenerator

**Command/CLI Layer:**
- Purpose: Entry points for three independent operations: document generation, Kotlin code generation, validation
- Location: `src/main/java/com/giffardtechnologies/restdocs/`
- Contains: DocGeneratorCommand, KotlinGeneratorCommand, DocValidatorCommand (all PicoCLI @CommandLine annotated)
- Depends on: Properties file parsing, all layers below
- Used by: JVM runtime as main classes

## Data Flow

**Document Generation Flow:**

1. DocGeneratorCommand reads properties file → resolves source YAML, template, output paths
2. DocGenerator delegates to DocValidator for validation
3. DocValidator deserializes YAML to storage.Document via Jackson
4. StorageToDomainMappers converts storage→domain with validation
5. Domain Document passed to Velocity template context
6. Template rendering tools (ObjectInspectionHelper, LinkTool) traverse domain model
7. VelocityEngine merges template with context → outputs HTML

**Kotlin Code Generation Flow:**

1. KotlinGeneratorCommand reads properties file → resolves source YAML, output directories, package names
2. KotlinGenerator calls DocValidator.getValidatedDocument() → storage.Document
3. StorageToDomainMappers converts storage→domain
4. Processors iterate over domain elements:
   - EnumProcessor processes domain NamedEnumeration → KotlinPoet TypeSpec
   - BitSetProcessor processes domain NamedBitSet → KotlinPoet TypeSpec
   - DataObjectProcessor + ObjectProcessor process domain DataObject/ObjectSpec → data class TypeSpec
   - MethodProcessor processes domain Method → request/response classes
5. KotlinPoet FileSpec writes to code directories

**Validation Flow:**

1. DocValidatorCommand reads properties file
2. DocValidator.validate() deserializes and transforms to domain (same as generation flows)
3. Mapper DSL builders validate constraints during transformation
4. Exceptions thrown at validation time (ValidationException, IllegalArgumentException) with clear field references

**State Management:**
- **Immutable flow**: Each layer produces immutable output (Vavr Array types in domain, KotlinPoet TypeSpec, final File outputs)
- **Context passing**: Document implements Context interface to provide type lookups during mapping
- **Type resolution**: Explicit during mapping phase; recursive type specs resolved via TypeSpec sealed subtypes

## Key Abstractions

**TypeSpec (Sealed Type):**
- Purpose: Represents all possible type structures in the API (primitives, objects, collections, enums, flags)
- Examples: `src/main/java/com/giffardtechnologies/restdocs/domain/type/TypeSpec.kt`
- Pattern: Sealed class with subtypes (DataSpec, ObjectSpec, ArraySpec, MapSpec, EnumSpec, BitSetSpec, TypeRefSpec, BooleanSpec)
- Recursive: ObjectSpec contains Array<Field>, ArraySpec contains nested TypeSpec, MapSpec contains TypeSpec for values

**Field & FieldListElement:**
- Purpose: Represent API fields with type, metadata, and include/exclude semantics
- Examples: `src/main/java/com/giffardtechnologies/restdocs/domain/Field.kt`, `src/main/java/com/giffardtechnologies/restdocs/domain/FieldListElement.kt`
- Pattern: Sealed trait for FieldListElement (Field vs FieldListIncludeElement); includes support composition via includes

**NamedType Interface:**
- Purpose: Marker for types that can be referenced by name across document
- Implementations: NamedEnumeration, NamedBitSet, DataObject
- Used by: TypeRefSpec for type lookups, validation

**Processor Pattern:**
- Purpose: Encapsulate generation logic for each domain type category
- Examples: `src/main/java/com/giffardtechnologies/restdocs/codegen/EnumProcessor.kt`, `src/main/java/com/giffardtechnologies/restdocs/codegen/DataObjectProcessor.kt`
- Pattern: Stateful generators that accept domain objects, produce KotlinPoet specs, write files

## Entry Points

**DocGeneratorCommand:**
- Location: `src/main/java/com/giffardtechnologies/restdocs/DocGeneratorCommand.kt`
- Triggers: `doc_generator -f docbuild.properties` (or default docbuild.properties)
- Responsibilities: Parse properties → resolve files → delegate to DocGenerator

**DocGenerator.generate():**
- Location: `src/main/java/com/giffardtechnologies/restdocs/DocGenerator.kt`
- Triggers: Called by DocGeneratorCommand or directly
- Responsibilities: Validate document → setup Velocity context → render template → write HTML

**KotlinGeneratorCommand:**
- Location: `src/main/java/com/giffardtechnologies/restdocs/KotlinGeneratorCommand.kt`
- Triggers: `kotlin_generator -f kotlin-gen.properties`
- Responsibilities: Parse properties → support local overrides → delegate to KotlinGenerator

**KotlinGenerator.generate():**
- Location: `src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt`
- Triggers: Called by KotlinGeneratorCommand
- Responsibilities: Validate & map document → process enums, bitsets, data objects → process methods → write files

**DocValidatorCommand:**
- Location: `src/main/java/com/giffardtechnologies/restdocs/DocValidatorCommand.kt`
- Triggers: `doc_validator -f docbuild.properties` (exit code based)
- Responsibilities: Parse properties → validate document → report errors

**DocValidator.getValidatedDocument():**
- Location: Shared (inferred from usage)
- Triggers: Called by all generators
- Responsibilities: Deserialize YAML → map to domain → return validated Document

## Error Handling

**Strategy:** Exceptions thrown early during mapping phase with ValidationException or domain-specific exceptions

**Patterns:**
- **ValidationException**: Thrown for semantic/validation issues during domain mapping (e.g., undefined type references)
- **IllegalArgumentException**: Thrown for structural errors (e.g., array missing items, collection missing key)
- **check() assertions**: Preconditions on model invariants (e.g., "Type reference to undefined type")
- **requireNotNull()**: Enforces required fields during mapping (e.g., longName for numeric enums)
- **CLI exit codes**: DocValidatorCommand returns CommandLine.ExitCode.SOFTWARE on JacksonException, OK otherwise

## Cross-Cutting Concerns

**Logging:** Console.println() and System.err.println() for errors and verbose output; DocGeneratorCommand --verbose flag controls println statements

**Validation:** Occurs at mapping time in StorageToDomainMappers via check(), requireNotNull(), and ValidationException; ensures invalid specs cannot reach codegen

**Authentication:** Not applicable; tool is static analysis and generation

**Type Resolution:** Delegated to Document.getTypeByName() (Context interface); called during field mapping and TypeRefSpec construction
