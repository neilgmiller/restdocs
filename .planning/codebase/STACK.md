# Technology Stack

**Analysis Date:** 2026-03-27

## Languages

**Primary:**
- Kotlin 2.1.20 (K2 compiler is default) - Main application logic, CLI commands, code generation
- Java - Legacy support and library compatibility

**Secondary:**
- Bash - Build scripts and command wrappers

## Runtime

**Environment:**
- JVM (Java Virtual Machine) with Gradle build system
- JVM toolchain target: Java 11

**Package Manager:**
- Gradle 8.12 (via gradle wrapper)
- Lockfile: `gradle/wrapper/gradle-wrapper.properties`

## Frameworks

**Core:**
- PicoCLI 4.7.5 - CLI command parsing and execution for `doc_generator`, `kotlin_generator`, `doc_validator`
- Apache Velocity 2.3 + Velocity Tools 3.1 - Template engine for HTML document generation

**Code Generation:**
- KotlinPoet 1.18.1 - Kotlin code generation with KSP support
- JavaPoet 1.13.0 - Java code generation

**Serialization:**
- Jackson 3.1.0 (Jackson 3.x alpha with `tools.jackson` group) - YAML/JSON data serialization
  - `jackson-dataformat-yaml` 3.1.0
  - `jackson-module-kotlin` 3.1.0
- Kotlin Serialization (kotlinx-serialization) 1.7.3 - Serialization annotations and JSON support
- Gson 2.10.1 - JSON processing support
- SnakeYAML 2.2 - YAML parsing

**Testing:**
- JUnit Jupiter (JUnit 5) 5.11.4 - Unit testing framework
- JUnit Platform Launcher 1.11.4 - Test execution platform

**Build/Dev:**
- Kotlin JVM Plugin 2.1.20
- Kotlin Serialization Plugin 1.7.3

## Key Dependencies

**Critical:**
- `futureproofenum` 1.0-SNAPSHOT - Internal Allego library for enum handling
- `mapstruct` 1.5.3.Final - Object mapping via annotation processor (uses `annotationProcessor`, not kapt)
- `kotlinx-datetime` 0.6.1 - Date/time handling
- `vavr` 0.10.3 - Functional programming primitives (immutable collections, functional interfaces)
- `guava` 33.2.1-jre - Google core utilities for Java

**Utilities:**
- `commons-io` 2.11.0 - File I/O utilities
- `commons-lang3` 3.13.0 - Language utilities
- `commons-text` 1.11.0 - Text processing utilities
- `findbugs-jsr305` 3.0.2 - JSR 305 annotations for nullability and thread safety
- `jetbrains-annotations` 24.1.0 - JetBrains nullability and inspection annotations

## Configuration

**Environment:**
- Properties-based configuration via `docbuild.properties`, `kotlin-gen.properties`
- Optional local overrides via `local.properties`
- Version injected at build time into `version.properties`

**Build Configuration:**
- `build.gradle.kts` - Main build configuration
- `gradle/libs.versions.toml` - Centralized dependency version management
- `settings.gradle.kts` - Project name configuration (restdocs)
- `gradle.properties` - Default Artifactory repository credentials

**Key Build Tasks:**
- Custom start scripts: `kotlin_generator`, `doc_validator` (derived from main `doc_generator`)
- Distribution tasks with optional custom install directory via `installDir` property
- Version property injection into resources during `processResources`

## Platform Requirements

**Development:**
- JVM 11 or higher (as specified in `jvmToolchain(11)`)
- Gradle 8.12
- Kotlin 2.1.20

**Production:**
- JVM 11 runtime
- Distribution delivered as executable JAR with wrapper scripts

**Repositories:**
- Maven Central (public dependencies)
- Local Ivy repository at `~/.ivy/repo` (internal libraries)
- Allego Artifactory at `https://dev-artifactory.allego-dev.com/artifactory/` (internal dependencies like `futureproofenum`)

---

*Stack analysis: 2026-03-27*
