# Phase 2: Kotlin Code Generation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-05
**Phase:** 02-kotlin-code-generation
**Areas discussed:** TypeRef asyncResponse handling, MethodClassNames exposure, Test approach

---

## TypeRef asyncResponse handling

| Option | Description | Selected |
|--------|-------------|----------|
| Skip — consistent with response | TypeRefSpec means the class already exists in the dto package. No new class generated. Mirrors the exact behavior of response TypeRefSpec today. | ✓ |
| Generate *AsyncResponse wrapper anyway | Always emit a *AsyncResponse class, even for typeref-based blocks. | |
| You decide | Leave to the planner. | |

**User's choice:** Skip — consistent with response

**Notes:** During discussion, the user pointed to the real API doc (`/Users/nmiller/Dev/workspace/allego1.0/doc/api-docs/API doc.yaml`) to investigate what TypeRef asyncResponse would look like in practice. Finding: the spec has zero `asyncResponse` entries; all 49 existing async methods use `response: { typeref: AsyncCapableResponse }` as a shared stub. New `asyncResponse` blocks will always be inline objects with fields. The TypeRef edge case is academic and should silently produce nothing (same as existing response TypeRefSpec behavior).

---

## MethodClassNames exposure

**Q1: Extend MethodClassNames or keep internal?**

| Option | Description | Selected |
|--------|-------------|----------|
| Keep internal to processMethod() | asyncResponseClassName computed and used only inside processMethod(). MethodClassNames stays as request+response. | |
| Extend MethodClassNames with asyncResponseClassName: ClassName? | Nullable ClassName? — null when method has no asyncResponse. SwiftAPIServerClient could reference it in the future. | ✓ |

**User's choice:** Extend MethodClassNames

**Q2: Null or Unit when no asyncResponse?**

| Option | Description | Selected |
|--------|-------------|----------|
| null | ClassName? = null when no asyncResponse. Different from responseClassName (which falls back to Unit). | ✓ |
| Unit::class.asClassName() fallback | Consistent with responseClassName's non-null contract. But Unit is semantically wrong here. | |

**User's choice:** null

**Notes:** The user prefers exposing asyncResponseClassName on MethodClassNames for forward compatibility (iOS/Swift async support), with null as the "not present" sentinel rather than a meaningless Unit fallback.

---

## Test approach

**Q1: Unit test, integration test, or both?**

| Option | Description | Selected |
|--------|-------------|----------|
| Unit test — build domain objects directly | Construct domain.Method with asyncResponse in-memory. Assert generated TypeSpec. Consistent with existing test style. | |
| Integration test — YAML fixture + full pipeline | Add test YAML in src/test/resources. Run KotlinGenerator.generate() end-to-end. | |
| Both — unit test for TypeSpec + smoke test for pipeline | Most coverage, most work. | ✓ |

**User's choice:** Both

**Q2: YAML fixture source?**

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal synthetic YAML | Small fixture in src/test/resources. Self-contained, no dependency on real API doc. | ✓ |
| Real API doc.yaml | Use the actual API doc. Realistic but heavyweight and couples test to external file. | |

**User's choice:** Minimal synthetic YAML

**Q3: Cases to cover in synthetic YAML?**

| Option | Description | Selected |
|--------|-------------|----------|
| All three spec cases | Case 1 (typeref response + asyncResponse), Case 2 (inline response + asyncResponse), Case 3 (no asyncResponse, regression). | ✓ |
| Just the happy path | Minimal fixture with one method with asyncResponse. | |

**User's choice:** All three spec cases

---

## Claude's Discretion

- Whether `createAsyncResponseClassDefinition()` is extracted as a parallel private helper or handled inline in `processMethod()`
- Whether `asyncResponseClassName` for a TypeRefSpec asyncResponse is null or the referenced ClassName (D-01 says null / skip)

## Deferred Ideas

None — discussion stayed within phase scope.
