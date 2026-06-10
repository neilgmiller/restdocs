# Milestones

## v1.0 — asyncResponse Block Support

**Shipped:** 2026-06-09
**Phases:** 1–3 | **Plans:** 5
**Branch:** allego-async

### Delivered

Added first-class `asyncResponse` support to the restdocs toolchain. Any method that returns a
job ID can now declare its eventual async payload in the same spec entry — visible in generated
HTML docs and typed Kotlin client code.

### Key Accomplishments

1. Added nullable `asyncResponse` field to storage/domain models with Jackson binding and full mapper wiring — enables YAML parsing without UnrecognizedPropertyException
2. Threaded `(String)->Unit` warningEmitter channel from `createMapper` through the full Jackson validation stack (Validatable, ValidationModule, ValidatingBeanDeserializerModifier, ValidatingDeserializer)
3. Implemented VALID-03/VALID-04 cross-field validation: warns when `response.job` exists without `asyncResponse`, errors when `asyncResponse` exists without `response.job`
4. Extended `MethodProcessor` to emit typed `*AsyncResponse` data classes for methods with inline ObjectSpec async payloads — 7 unit tests + smoke test
5. Updated `rest_api_doc.vm` (canonical live template) with asyncResponse section rendering: heading, prose note, inline field table, and typeRef link branch — 6 integration tests

### Stats

- 3 phases, 5 plans
- 283 commits on branch
- 193 files changed (17,053 insertions, 1,253 deletions)
- Timeline: 2026-06-02 → 2026-06-09 (7 days)

### Archive

- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.0-REQUIREMENTS.md`
