# restdocs — asyncResponse Block Support

## What This Is

A Kotlin toolchain for building API documentation and generating Kotlin client code from YAML spec files.
It has three CLI commands: `doc_generator` (HTML docs), `kotlin_generator` (Kotlin data classes), and
`doc_validator` (spec validation). v1.0 shipped first-class support for documenting async method return
payloads via a new `asyncResponse` block in the YAML format.

## Core Value

Any method that returns a job ID also declares its eventual async payload in the same spec entry — so
callers never have to hunt through prose or source code to understand what they'll get back.

## Requirements

### Validated

- ✓ YAML API spec deserialization via Jackson (storage model) — existing
- ✓ Storage → Domain mapping with validation (StorageToDomainMappers) — existing
- ✓ HTML documentation generation via Velocity templates — existing
- ✓ Kotlin code generation: data classes, enums, bitsets, request/response classes — existing
- ✓ `doc_validator` CLI validates spec structure and type references — existing
- ✓ TypeSpec sealed hierarchy (DataSpec, ObjectSpec, ArraySpec, MapSpec, EnumSpec, BitSetSpec, TypeRefSpec) — existing
- ✓ Named type resolution via Document.getTypeByName() — existing
- ✓ TypeRef support: typeref on fields and method responses — existing
- ✓ Add `asyncResponse` field to storage `Method` model — v1.0
- ✓ Add `asyncResponse` field to domain `Method` model — v1.0
- ✓ Map `asyncResponse` in StorageToDomainMappers — v1.0
- ✓ Validator: warn when `response` contains `job` field but `asyncResponse` is absent (VALID-03) — v1.0
- ✓ Validator: error when `asyncResponse` is present but `response` has no `job` field (VALID-04) — v1.0
- ✓ `asyncResponse` recognized as a valid method-level YAML key (VALID-01) — v1.0
- ✓ HTML renderer: emit `asyncResponse` as a second response table with header "Async Response (via getAsyncJobStatus)" — v1.0
- ✓ HTML renderer: prose note that content appears in `jr` when `jobStatus` is `0` — v1.0
- ✓ Kotlin generator: emit a `*AsyncResponse` data class for methods with `asyncResponse` block — v1.0

### Active

- [ ] Add `AsyncJobResponse` canonical typeref definition (replaces `AsyncCapableResponse` for new entries)
- [ ] Migrate existing 49 `AsyncCapableResponse` methods to use the new format (opportunistic)

### Out of Scope

- Migrating existing 49 `AsyncCapableResponse` usages en masse — migrate opportunistically, no urgency
- Hyperlink cross-linking from asyncResponse sections to getAsyncJobStatus method entry — deferred
- Formal JSON Schema / grammar update for the YAML format — follow-on
- `jr` field cross-referencing / per-method typing back to asyncResponse definitions — out of scope
- Client SDK stub generation from asyncResponse — out of scope
- Removing `AsyncCapableResponse` — keep as alias, backward compatibility required
- Path-based dispatch in `MethodProcessor` — methods with no `id` (using `path` only) are rejected with a clear error; full path-based routing is a future feature

## Context

Shipped v1.0 with asyncResponse support across all three toolchain outputs (validator, HTML, Kotlin codegen).
The feature is additive and backward compatible — 49 existing `AsyncCapableResponse` entries continue to work.
Canonical live template (`rest_api_doc.vm`) replaced a diverged stub during Phase 3 execution.

This is a brownfield project with an existing codebase map at `.planning/codebase/`. The toolchain
is used internally at Allego to generate API documentation from YAML spec files.

## Constraints

- **Compatibility**: Must not break 49 existing `AsyncCapableResponse`/`AsyncCapableResponse` usages
- **Architecture**: Follow the existing storage/domain/mapper/codegen/htmlgen layer separation
- **Tech stack**: Kotlin 2.1.20, JVM 11, Jackson 3.x, KotlinPoet 1.18.1, Velocity 2.3
- **Validation timing**: Validation occurs at mapping time, same as all other constraints

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| asyncResponse reuses existing Response type verbatim | No new types needed; storage/domain/mapper follow existing patterns | ✓ Good |
| kotlin_generator emits `*AsyncResponse` data class | User chose to generate typed async response classes, not doc-only | ✓ Good |
| TypeRefSpec asyncResponse produces no generated class | No inline fields to emit; silently skips | ✓ Good |
| AsyncJobResponse typeref replaces AsyncCapableResponse for new entries | Cleaner canonical name; AsyncCapableResponse kept as alias | — Pending (migrate opportunistically) |
| Minimal viable rendering (table + prose note, no hyperlink) | Cross-linking deferred; get content visible first | ✓ Good |
| Migrate-opportunistically policy for 49 existing entries | No urgency; new entries use new format, existing entries migrate when touched | ✓ Good |
| Replace stub template with canonical live template | Stub had diverged too far — wrong loop variable, missing sidebar nav, broken response rendering | ✓ Good |

---
*Last updated: 2026-06-09 after v1.0 milestone (asyncResponse block support complete)*
