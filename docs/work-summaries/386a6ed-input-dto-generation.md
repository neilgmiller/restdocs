# Input DTO Generation — Commit 386a6ed

**Branch:** `allego`  
**Date:** 2026-06-17  
**Files changed:** 7 (+687 / −9)

---

## Overview

This commit introduces usage-aware code generation for Kotlin `DataObject` classes. Before this
change, every `DataObject` was always emitted as a data class with constructor defaults — which
is correct for deserialization but wrong when the same type is used as a request parameter. The
change adds a pre-generation analysis pass that classifies each `DataObject` by how it appears
across the service, then generates the right class variant(s) based on that classification.

---

## The Problem

The Kotlin code generator emits constructor defaults (e.g. `val label: String = "hello"`) for all
`DataObject` classes in `clientPackage.dto`. This is intentional for **response deserialization**:
`kotlinx.serialization` fills in missing JSON fields using those defaults, so a response that with
an optional field with a default always has a value for that field.

The same defaults are harmful when a `DataObject` is used as a **request parameter**. A developer
calling an API method should be explicit about every field they send. If the class has defaults, the
compiler won't force them to supply values — they can accidentally omit fields and send stale or
zero-value data to the server. The server, not the client, owns those defaults.

The tricky case is `DataObject` types used in **both** contexts simultaneously — typically the
create/update pattern where the request body is also the response type (e.g., a `Foo` resource
that the server returns after creation, and the client also sends to create it). These ~12 objects
cannot be served by a single generated class because Kotlin has no way to conditionally omit
constructor defaults.

> Inline `Params` sub-objects (the nested `Params` class inside method processors) were already
> generated with `initializeWithDefault = false` and are not affected by this change.

---

## The Solution: Usage Analysis + Dual-Class Generation

### Phase 1 — Usage Analysis (`DataObjectUsageClassifier`)

Before any code generation begins, a new `DataObjectUsageClassifier` walks the entire document's
service methods and builds two sets:

- **`parameterUsed`** — every `DataObject` reachable from the parameter side of any method
- **`responseUsed`** — every `DataObject` reachable from the response side of any method

The traversal is **transitive and cycle-safe**: when a `TypeRefSpec` is encountered and the
referenced type is a `DataObject`, the classifier recurses into that object's own fields. The set
itself acts as the visited guard — if a name is already in the set, recursion stops, which also
prevents infinite loops on self-referencing types.

The traversal covers:
- `method.parameters` (the already-flattened field list)
- `method.response.typeSpec`
- `method.asyncResponse.typeSpec`
- `service.common.parameters`
- Collection wrappers: `ArraySpec`, `MapSpec`, inline `ObjectSpec`

`FieldListIncludeElement`s do **not** need special traversal. Their fields are already inlined into
`method.parameters` by the time the classifier runs, so the included object's fields are seen
during the normal flat-field walk.

### Phase 2 — Classification

Each `DataObject` is classified at generation time:

| Classification | Condition | Generation |
|---|---|---|
| `ResponseOnly` | in `responseUsed`, not `parameterUsed` | `Foo.kt` in `dto` — with constructor defaults (unchanged) |
| `ParameterOnly` | in `parameterUsed`, not `responseUsed` | `FooInput.kt` in `requests.dto` — without defaults |
| `Mixed` | in both sets | `Foo.kt` in `dto` (with defaults) **and** `FooInput.kt` in `requests.dto` (without defaults) |

Types that appear in neither set default to `ResponseOnly` — the safe, backward-compatible
choice.

### Phase 3 — TypeRef Substitution in Parameter Contexts

When `FieldAndTypeProcessor` resolves a `TypeRefSpec` to a class name, it now tracks whether it
is operating in a **parameter context** (i.e., `initializeWithDefault = false`). In that context,
if the referenced type is classified `Mixed` or `ParameterOnly`, the generated field type uses
`FooInput` from `requests.dto` rather than `Foo` from `dto`. This ensures that nested references
are also substituted — a `FooInput` that contains a field of type `Bar` (where `Bar` is also
`Mixed`) will correctly declare that field as `BarInput`.

---

## Package Structure

```
clientPackage.dto              — DataObjects (ResponseOnly + Mixed response variant with defaults)
clientPackage.requests.dto     — Input variants (Mixed + ParameterOnly, no defaults) [NEW]
clientPackage.requests         — HTTP request/response wrappers (unchanged)
clientPackage.requests.serialization — serialization utils (unchanged)
```

---

## Files Changed

### New: `codegen/DataObjectUsageClassifier.kt`

The analysis engine. Constructed with the full `Document`, runs the traversal in `init`, then
exposes `classify(name: String): DataObjectClassification`. The `DataObjectClassification` enum
(`ResponseOnly`, `ParameterOnly`, `Mixed`) is defined in the same file.

### Modified: `codegen/DataObjectProcessor.kt`

Now accepts both `requestsDtoPackage` and `classifier` in its constructor. The
`generateDataObjectClassFile` method dispatches on `classifier.classify(dataObject.typeName)`:

- `ResponseOnly` → writes `Foo` with defaults (same as before)
- `ParameterOnly` → writes `FooInput` without defaults (new `processInputClassDefinition`)
- `Mixed` → writes both

A new private `processInputClassDefinition` method generates the `FooInput` variant using
`ClassName(requestsDtoPackage, typeName + "Input")` and `initializeWithDefault = false`.

### Modified: `codegen/FieldAndTypeProcessor.kt`

Accepts two new optional constructor parameters: `classifier` and `requestsDtoPackage`. The
`getTypeName` method receives a new `parameterContext: Boolean` flag (threaded down from
`createPropertySpec` via `initializeWithDefault`). When `parameterContext = true` and the
referenced type is a `DataObject` classified as `Mixed` or `ParameterOnly`, the returned
`TypeName` is `ClassName(requestsDtoPackage, referenceName + "Input")`.

### Modified: `KotlinGenerator.kt`

- Declares `requestsDtoPackage = requestsPackage + ".dto"`
- Instantiates `DataObjectUsageClassifier(document)` before the generation loop
- Passes `classifier` and `requestsDtoPackage` into both `FieldAndTypeProcessor` and
  `DataObjectProcessor`

### Modified: `codegen/ObjectProcessor.kt`

Minor: threads `initializeWithDefault` through the `processObjectToTypeSpec` overload that
accepts an `ObjectSpec` directly, so callers don't need to unpack it themselves.

---

## Tests

### `DataObjectUsageClassifierTest` (219 lines, 12 tests)

Covers the classifier's behaviour in isolation:

- Unreferenced type defaults to `ResponseOnly`
- Direct `response` / `asyncResponse` TypeRefSpec → `ResponseOnly`
- Direct parameter TypeRefSpec → `ParameterOnly`
- Same type in both → `Mixed`
- Transitive traversal (nested DataObject inherits parent's context)
- Collection wrappers: `ArraySpec`, `MapSpec`
- Inline `ObjectSpec` traversal
- `service.common.parameters` as parameter context
- Self-referencing DataObject does not cause infinite recursion

### `DataObjectProcessorTest` (310 lines, 12 tests)

End-to-end tests against a real temp directory — builds the full processor stack and inspects
generated `.kt` file contents:

- `ResponseOnly`: generates `Foo` with default value; no `FooInput`
- `ParameterOnly`: generates `FooInput` in `requests.dto`; no `Foo` in `dto`; defaults suppressed
- `Mixed`: generates both; `Foo` retains defaults; `FooInput` suppresses them; `FooInput` is
  named `data class FooInput`
- TypeRef substitution: `FooInput` whose field is a `Mixed Bar` references `BarInput`, not `Bar`
- Response variant of `Mixed Foo` still uses `Bar`, not `BarInput`
- `ParameterOnly FooInput` whose field is a `ParameterOnly Bar` references `BarInput`

---

## Design Decisions

**Why a pre-generation pass instead of per-object analysis?**  
A single object can't know its own usage — that depends on all service methods. Running the
analysis once at startup and caching the two sets keeps the generator's loop simple and avoids
re-walking the method graph for every `DataObject`.

**Why `FooInput` rather than a flag on `Foo`?**  
Kotlin constructors cannot conditionally omit defaults. A single class cannot serve both contexts.
Two classes with different constructors is the only sound option.

**Why is `ParameterOnly` also named `FooInput` (not just `Foo`)?**  
Consistency: any type that lacks defaults lives in `requests.dto` under the `Input` suffix. This
makes it unambiguous which variant a developer is holding when reading generated code.

**Why does `ParameterOnly` go into `requests.dto` rather than stay in `dto`?**  
The `dto` package is the "read" package — it is what you get back from the server. Putting
parameter-only types there would blur that distinction. `requests.dto` signals "this is what you
send."

**Why not walk `FieldListIncludeElement`s separately?**  
Include elements are already inlined into `method.parameters` by the time the classifier runs.
The included DataObject's fields are seen during the normal flat-field walk. The included
DataObject itself never appears as a `TypeRefSpec` in generated code (the `Params` class holds
each field directly, not a reference to the included object), so separate traversal would only
affect whether the *included object's standalone `Foo.kt`* loses defaults — a harmless edge case
not worth the added complexity.
