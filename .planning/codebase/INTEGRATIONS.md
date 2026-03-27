# External Integrations

**Analysis Date:** 2026-03-27

## APIs & External Services

**Code Generation Clients:**
- APIServerClient (generated) - Kotlin multi-platform library generation for API access
  - Location: Generated via `KotlinGeneratorCommand` at `src/main/java/com/giffardtechnologies/restdocs/KotlinGeneratorCommand.kt`
  - Purpose: Outputs Swift/Kotlin client stubs for REST API consumption

**RESTUtils:**
- RESTUtils (internal custom library)
  - Location: `manual-libs/RESTUtils.jar`
  - Purpose: Custom REST utilities for HTTP operations

## Data Storage

**Databases:**
- None - Application is file-based, not database-backed

**File Storage:**
- Local filesystem only
  - Input: YAML/JSON document definitions via `docbuild.properties` sourceFile path
  - Output: Generated HTML documentation, Kotlin source code
  - Template files: `.vm` Velocity template files (default: `rest_api_doc.vm`)

**Caching:**
- None detected

## Input/Output Processing

**Input Formats:**
- YAML - Primary input format for API descriptor documents
  - Parsed via `YAMLMapper` (Jackson 3.1.0)
  - Location: `src/main/java/com/giffardtechnologies/restdocs/jackson/JacksonMapperBuilder.kt`
  - Custom deserializers: `YesNoBooleanDeserializer`, `TrueOnNullBooleanDeserializer`

**Output Formats:**
- HTML - Generated via Velocity template engine
  - Location: `src/main/java/com/giffardtechnologies/restdocs/DocGenerator.kt`
  - Template context variables: `document`, `esc` (EscapeTool), `link` (LinkTool), `text` (PlainTextTool), `helper` (ObjectInspectionHelper)
- Kotlin source code - Generated via KotlinPoet
  - Location: `src/main/java/com/giffardtechnologies/restdocs/KotlinGenerator.kt`
- Java source code - Generated via JavaPoet
  - Location: `src/main/java/com/giffardtechnologies/restdocs/codegen/`

## Authentication & Identity

**Auth Provider:**
- None - Application is not a service requiring authentication
- Gradle repository access requires credentials stored in `~/.gradle/gradle.properties`:
  - `allegoArtifactoryUser`
  - `allegoArtifactoryPassword`
  - Used for accessing Allego Artifactory for internal dependencies

## Monitoring & Observability

**Error Tracking:**
- None - No external error reporting service

**Logs:**
- Console-based via `println()` and `System.err.println()`
- Verbose mode toggle via `-v`/`--verbose` flag in `DocGeneratorCommand`

## CI/CD & Deployment

**Hosting:**
- None - CLI application deployed as executable JAR with scripts
- Optional custom install directory via `installDir` property in `local.properties`

**CI Pipeline:**
- None detected - No GitHub Actions, Jenkins, or other CI service integration

**Artifact Distribution:**
- Gradle distributions task generates distributable archives
- Start scripts created: `doc_generator`, `kotlin_generator`, `doc_validator`

## Environment Configuration

**Required env vars:**
- None required - Properties-based configuration only
- JVM heap settings via standard Java options if needed

**Configuration Files (Build-time):**
- `docbuild.properties` - DocGenerator configuration (sourceFile, outputFile, templateFile)
- `kotlin-gen.properties` - KotlinGenerator configuration (package names, output paths)
- `local.properties` - Optional local overrides (installDir)

**Secrets Location:**
- Gradle properties: `~/.gradle/gradle.properties` (Allego Artifactory credentials)
- No application secrets required at runtime

## Webhooks & Callbacks

**Incoming:**
- None - Application is not a server

**Outgoing:**
- None - Application does not call external services

## Version Management

**Version Source:**
- `version = "2.3-SNAPSHOT"` in `build.gradle.kts`
- Injected into resources at build time: `src/main/resources/version.properties`
- Provider: `src/main/java/com/giffardtechnologies/restdocs/VersionProvider.kt`

## Validation & Constraints

**Jackson Validation:**
- Custom validation framework at `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/`
- Components:
  - `Validatable` interface for objects requiring validation
  - `ValidationModule` for Jackson integration
  - `ValidatingDeserializer` and `ValidatingBeanDeserializerModifier` for validation during deserialization
  - `ValidationException` for validation failures
- Location: `src/main/java/com/giffardtechnologies/restdocs/jackson/validation/`

**Reference Document Validation:**
- Alternate validation mode via `--reference` flag
- Configuration: `src/main/java/com/giffardtechnologies/restdocs/DocValidator.kt`
- Options: `longNameIsOptional`, `longNameAllowsSpaces`

---

*Integration audit: 2026-03-27*
