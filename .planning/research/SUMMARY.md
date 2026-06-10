# Project Research Summary

**Project:** restdocs asyncResponse Block Support
**Domain:** Kotlin toolchain extension (Jackson/Velocity/KotlinPoet pipeline)
**Researched:** 2026-06-02
**Confidence:** HIGH

## Executive Summary

This is a brownfield feature addition to an existing Kotlin API documentation toolchain. The toolchain has a well-established layered architecture: YAML deserialization via Jackson into a storage model, mapping to a domain model, then two independent output pipelines (HTML via Velocity templates, Kotlin code via KotlinPoet). The `asyncResponse` block is structurally identical to the existing `response` block, meaning every layer already has the abstractions needed — `Response`, `TypeSpec`, `ObjectProcessor`, `mapToModel()` — and the implementation is primarily about wiring a new nullable field through each layer.

The recommended approach is strictly bottom-up: add the field to the storage model first (unblocking Jackson parsing), then domain model + mapper, then the two output pipelines in parallel. This order is non-negotiable because Jackson 3.x fails hard on unknown YAML keys — nothing can be tested until the storage model accepts `asyncResponse`. The existing code reuse is excellent; no new classes, abstractions, or dependencies are needed. The total estimated scope is 7 files changed with roughly 50-80 lines of new code.

The primary risks are: (1) forgetting that the HTML pipeline uses the storage model directly while codegen uses the domain model (requiring changes in both models), (2) the cross-field validation logic for detecting "has job" in typeref-based responses, and (3) ambiguity about which Velocity template is the production template for method rendering. The first two are mitigated by the implementation order; the third requires resolution before or during the HTML rendering phase.

## Key Findings

### Recommended Stack

No new dependencies required. The feature is purely additive within the existing type system.

**Core technologies (unchanged):**
- **Jackson 3.x (tools.jackson:jackson-databind:3.1.0):** YAML deserialization with automatic `Validatable` integration — asyncResponse gets structural validation for free
- **KotlinPoet 1.18.1:** Code generation for `*AsyncResponse` data classes — same `ObjectProcessor.processObjectToTypeSpec()` path as sync responses
- **Velocity 2.3:** HTML template rendering using storage model directly — `#if($method.asyncResponse)` guard is sufficient for null safety

**Critical version note:** Jackson 3.x defaults `FAIL_ON_UNKNOWN_PROPERTIES` to true and the project does NOT override this. Storage model field must exist before any YAML fixture uses the key.

### Expected Features

**Must have (table stakes):**
- `asyncResponse` field on storage `Method` — without this, YAML parsing fails entirely
- `asyncResponse` field on domain `Method` — without this, codegen has no data
- Mapper wiring between storage and domain — connects the two pipelines
- Validator structural rules (automatic via TypeSpec) — prevents malformed async blocks
- Validator cross-field error: asyncResponse without job in response — spec invariant
- HTML rendering: second response table with "Async Response (via getAsyncJobStatus)" header
- HTML rendering: prose note about `jr` field when `jobStatus` is `0`
- Kotlin codegen: emit `*AsyncResponse` data class for ObjectSpec async responses

**Should have (enhance correctness):**
- Validator warning: response has `job` but no `asyncResponse` (flags undocumented async payloads)
- Smart typeref recognition: treat `AsyncCapableResponse`/`AsyncJobResponse` as "has job" without field inspection
- Proper error identifier strings in mapper (say "asyncResponse" not "response" in error messages)

**Defer (v2+):**
- Hyperlink cross-linking to getAsyncJobStatus method — explicitly out of scope per PROJECT.md
- Migration of 49 existing `AsyncCapableResponse` usages — opportunistic only
- Formal JSON Schema for YAML format — follow-on work

### Architecture Approach

The toolchain has two parallel output pipelines consuming different model layers. HTML generation reads the storage model directly (no domain mapping). Kotlin code generation reads the domain model (mapped from storage). Both pipelines need `asyncResponse` but at different abstraction levels. The storage model change unblocks BOTH validation AND HTML rendering simultaneously; the domain model change is only needed for Kotlin codegen.

**Major components:**
1. **Storage `Method` (Jackson target)** — YAML deserialization, structural validation via `Validatable`
2. **Domain `Method` + Mapper** — type-resolved, semantically enriched model for codegen
3. **`Method.validate()` cross-field checks** — enforces job/asyncResponse invariants
4. **Root `rest_api_doc.vm` template** — renders methods with response tables (NOT `docs/rest_api_doc.vm`)
5. **`MethodProcessor.processMethod()`** — generates request/response/asyncResponse classes via KotlinPoet

### Critical Pitfalls

1. **Jackson unknown property failure** — Jackson 3.x throws `UnrecognizedPropertyException` on any YAML key not declared on the storage model. The storage `Method` field MUST be added before any YAML test fixture uses `asyncResponse`. Hard prerequisite for all other work.

2. **Two-pipeline model divergence** — HTML uses storage model, codegen uses domain model. If you add `asyncResponse` to only one model, one output will silently produce nothing. Always update storage, domain, mapper, and template together as an atomic concern.

3. **TypeRef "has job" detection complexity** — Validating that `response` contains a `job` field is non-trivial when the response is a typeref (must recognize `AsyncJobResponse`/`AsyncCapableResponse` by name) or uses includes. Start with a heuristic (name-check + field scan) and make the warning-level check lenient.

4. **Template ambiguity** — Two `rest_api_doc.vm` files exist: root-level (renders methods) and `docs/` (renders data objects only). The root-level template is the target for asyncResponse rendering, but which template is active in production must be verified before implementing Phase 3.

5. **Class name collision in codegen** — Methods ending in "Async" would produce awkward `*AsyncAsyncResponse` names. Verify against actual spec method names before finalizing the naming pattern.

## Implications for Roadmap

### Phase 1: Storage Model + Validation Foundation
**Rationale:** Hard prerequisite — Jackson fails on unknown keys, so nothing can be tested until the storage model accepts `asyncResponse`. Validation rules 1-2 come free with the field addition.
**Delivers:** YAML files with `asyncResponse` parse without error; structural validation runs automatically; cross-field validation (error: asyncResponse without job) prevents invalid specs.

### Phase 2: Domain Model + Mapper + Codegen
**Rationale:** The domain model and mapper are tightly coupled. Codegen depends on the domain model. These three form a natural unit.
**Delivers:** `*AsyncResponse` Kotlin data classes generated for methods with async payloads; full storage-to-domain pipeline connected.

### Phase 3: HTML Rendering
**Rationale:** Depends only on the storage model (Phase 1). Sequenced after Phase 2 to allow template ambiguity question to be resolved during Phase 1.
**Delivers:** asyncResponse appears in generated HTML documentation with proper heading, prose note, and field table.

### Phase Ordering Rationale

- **Phase 1 first** — Jackson 3.x makes it impossible to test anything without the storage field.
- **Phase 2 before Phase 3** — Codegen is more complex; doing it while storage model is fresh reduces context-switching.
- **Phases 2 and 3 are independent** after Phase 1 completes.
- **Validation cross-field logic in Phase 1** — prevents bad specs from being written before anyone relies on the feature.

## Key Open Question

**Which Velocity template is the production template for method rendering?**

Two templates exist: `/rest_api_doc.vm` (root, ~180 lines, renders methods) and `/docs/rest_api_doc.vm` (~255 lines, renders data objects only). Must be verified by checking `docbuild.properties` or `DocGenerator` template loading path before Phase 3 implementation begins. If the `docs/` template is production and needs method rendering added, Phase 3 scope expands significantly.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | No new dependencies; all patterns verified against source code |
| Features | HIGH | Requirements derive from explicit spec document and PROJECT.md decisions |
| Architecture | HIGH | Two-pipeline discovery verified by tracing DocGenerator and KotlinGenerator entry points |
| Pitfalls | HIGH | All pitfalls derived from direct source code analysis |

**Overall confidence:** HIGH

---
*Research completed: 2026-06-02*
*Ready for roadmap: yes*
