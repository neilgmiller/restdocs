# STRUCTURE

## Directory Layout

```
restdocs/
├── src/
│   ├── main/
│   │   ├── java/                          # Kotlin source (uses java dir convention)
│   │   │   ├── com/giffardtechnologies/
│   │   │   │   ├── meter/                 # Utility: File.kt
│   │   │   │   └── restdocs/
│   │   │   │       ├── codegen/           # Kotlin code generation processors
│   │   │   │       ├── domain/            # Domain model (rich types, resolved)
│   │   │   │       │   ├── dsl/           # DSL builder wrappers for domain types
│   │   │   │       │   └── type/          # TypeSpec, NamedType sealed types
│   │   │   │       ├── htmlgen/           # HTML generation utilities
│   │   │   │       ├── jackson/           # Jackson config and custom deserializers
│   │   │   │       │   └── validation/    # Jackson deserialization-time validation
│   │   │   │       ├── mappers/           # Storage → Domain mapper (MapStruct)
│   │   │   │       ├── model/             # FieldPath / FieldPathSet utilities
│   │   │   │       ├── storage/           # Storage model (raw Jackson deserialization)
│   │   │   │       │   └── type/          # DataType, TypeSpec, Field, FieldElementList
│   │   │   │       └── vavr/              # Vavr array extensions
│   │   │   └── org/apache/velocity/tools/ # Bundled Velocity tools patch
│   │   └── resources/                     # Velocity templates, static assets
│   └── test/
│       └── kotlin/
│           └── com/giffardtechnologies/restdocs/
│               ├── domain/                # Domain-layer tests
│               └── storage/type/          # Storage-layer tests
├── docs/
│   ├── html/css/                          # Output CSS
│   └── restdocs-tool/                     # Bundled previous release for self-documentation
├── gradle/
│   └── wrapper/
├── manual-libs/                           # Local jar dependencies (futureproofenum)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

## Key Source Locations

| Purpose | Path |
|---------|------|
| CLI entry point (doc gen) | `src/main/java/com/giffardtechnologies/restdocs/DocGeneratorCommand.kt` |
| CLI entry point (kotlin gen) | `src/main/java/com/giffardtechnologies/restdocs/KotlinGeneratorCommand.kt` |
| CLI entry point (validator) | `src/main/java/com/giffardtechnologies/restdocs/DocValidatorCommand.kt` |
| Doc generation logic | `src/main/java/com/giffardtechnologies/restdocs/DocGenerator.kt` |
| Kotlin code generation | `src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt` |
| Storage → Domain mapper | `src/main/java/com/giffardtechnologies/restdocs/mappers/StorageToDomainMappers.kt` |
| Field/type code generation | `src/main/java/com/giffardtechnologies/restdocs/codegen/FieldAndTypeProcessor.kt` |
| Method code generation | `src/main/java/com/giffardtechnologies/restdocs/codegen/MethodProcessor.kt` |
| Jackson setup | `src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt` |
| Domain TypeSpec | `src/main/java/com/giffardtechnologies/restdocs/domain/type/TypeSpec.kt` |
| Storage TypeSpec | `src/main/java/com/giffardtechnologies/restdocs/storage/type/TypeSpec.kt` |
| Domain FieldElementList | `src/main/java/com/giffardtechnologies/restdocs/domain/FieldElementList.kt` |
| Storage FieldElementList | `src/main/java/com/giffardtechnologies/restdocs/storage/type/FieldElementList.kt` |

## Naming Conventions

- **Package names**: lowercase, dot-separated (`com.giffardtechnologies.restdocs.codegen`)
- **Class names**: PascalCase (`FieldAndTypeProcessor`, `StorageToDomainMappers`)
- **Source directory**: Kotlin files live under `src/main/java/` (not `src/main/kotlin/`) — legacy Java dir convention
- **Test directory**: Tests use `src/test/kotlin/` matching package structure
- **Storage vs domain**: Parallel package layout — `storage/` mirrors `domain/` for same-named types (e.g., `storage.Document` vs `domain.Document`)
- **Command suffix**: CLI entry points use `*Command.kt` naming; core logic in matching `*.kt` without suffix
- **Processor suffix**: Code generation classes use `*Processor.kt` pattern
- **Spec suffix**: DSL builder classes use `*Spec.kt` pattern

## Notable Layout Decisions

- **Dual source language convention**: Source root is `src/main/java/` despite all code being Kotlin — this is a holdover from original Java project structure
- **Bundled Velocity tools**: `org/apache/velocity/tools/view/` is bundled directly in source, not pulled as a dependency
- **Manual libs**: `manual-libs/` holds local jars not available in Maven (e.g., `futureproofenum`)
- **Parallel storage/domain models**: Both `storage/` and `domain/` define `Document`, `Method`, `Field`, etc. — storage types are deserialized from YAML/JSON, domain types are the resolved/enriched form used by generators
- **Self-documentation**: `docs/restdocs-tool/` contains a bundled previous release used to generate documentation for the tool itself
