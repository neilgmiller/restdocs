# restdocs — Project Guide

## Project Context

This is the restdocs toolchain — a Kotlin CLI for generating API documentation (HTML) and Kotlin client code from YAML spec files. Three commands: `doc_generator`, `kotlin_generator`, `doc_validator`.

**Current milestone:** asyncResponse block support — adding a new optional `asyncResponse` sibling to `response` in the YAML format, with validator, HTML renderer, and Kotlin codegen changes.

Planning docs: `.planning/`
Spec: `.claude/docbuild-async-response-spec.md`

## GSD Workflow

This project uses GSD for structured phase-based development.

**Current phase:** Phase 1 — Storage Model + Validation Foundation
**State:** `.planning/STATE.md`
**Roadmap:** `.planning/ROADMAP.md`
**Requirements:** `.planning/REQUIREMENTS.md`

### Workflow Commands

```
/gsd:discuss-phase N    — gather context before planning
/gsd:plan-phase N       — create PLAN.md for the phase
/gsd:execute-phase N    — execute all plans in a phase
/gsd:verify-work        — verify phase goals were achieved
/gsd:progress           — check status and advance
```

### Phase Execution Rules

- Always read PLAN.md before touching any code
- Each plan completes atomically with a commit before moving to the next
- Verify against success criteria in ROADMAP.md, not just task completion
- Update STATE.md after each plan

## Build

```bash
./gradlew build           # compile + test
./gradlew installDist     # build + install start scripts
```

Generated start scripts in `build/install/restdocs/bin/`: `doc_generator`, `kotlin_generator`, `doc_validator`.

## Key Architecture Notes

- **Two pipelines**: HTML generation uses `storage.Document` directly (Velocity). Kotlin codegen uses `domain.Document` (via StorageToDomainMappers). Changes that affect both outputs need changes in both models.
- **Jackson 3.x** defaults `FAIL_ON_UNKNOWN_PROPERTIES` to true — no override in this project. Unknown YAML keys are hard errors.
- **Validation at mapping time**: errors thrown as `ValidationException` or `IllegalArgumentException` during storage→domain mapping
- Source root is `src/main/java/` for Kotlin files (legacy convention — don't change)

## Tech Stack

- Kotlin 2.1.20, JVM 11, Gradle 8.12
- Jackson 3.1.0 (`tools.jackson` group), KotlinPoet 1.18.1, Velocity 2.3, PicoCLI 4.7.5
- No kapt — MapStruct uses `annotationProcessor`
