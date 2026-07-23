# restdocs

A code-generation and documentation tool that reads an API spec file and produces HTML docs, generated Kotlin client code, and validation reports.

## Language

**Storage (model)**:
The Jackson-deserialized representation of the spec exactly as the API author wrote it in the file — geared toward the file format, not the concepts it describes. Lives in the `storage` package.
_Avoid_: Wire model, raw model, spec model (when referring generically to this layer).

**Domain (model)**:
The resolved, validated, type-safe representation of the API being described, built from the Storage model via mappers. This is what every generator (HTML, Kotlin, validator) actually operates on. Lives in the `domain` package.
_Avoid_: Resolved model, output model.

**Response**:
The description of what a Method returns. A genuine domain concept present in both Storage and Domain form — but the two forms are not structurally parallel: the Storage form is expressed directly as a TypeSpec (inheriting its type/restriction/field machinery), while the Domain form is a thin wrapper around a TypeSpec plus a description and a no-payload flag.

**Resource** _(planned, not yet wired in)_:
A URI-grouped set of Methods. Present in both Storage and Domain packages but not yet referenced by Service or Document — Service currently exposes a flat list of Methods with no URI grouping. This is an intentional stub for a not-yet-built feature, not dead code to remove.
_Avoid_: Action (as a synonym for Method — only appears on the unwired Resource, and conflicts with Method elsewhere).

**FieldReference**:
The literal, author-facing representation of a field path exactly as someone inputs it — the raw input into the field-inclusion generation process.
_Avoid_: Field path (when specifically meaning this input form — use FieldPath for the modeling-side representation instead).

**FieldPath / FieldPathSet**:
The internal modeling representation used to actually compute includeOnly/excluding resolution logic, once a FieldReference has been read in. Distinct from FieldReference: this is modeling logic, not input.

**TypeSpec**:
The description of a value's shape/type — the core concept in both the Storage and Domain models (see ADR-0002). In `codegen/`, this is our own type, distinct from KotlinPoet's own `TypeSpec` class, which is always aliased as `KpTypeSpec` when both appear in the same file.
_Avoid_: Using bare `TypeSpec` unqualified for KotlinPoet's type in any file that also references our own.

**parsedAs**:
A step in the serialization pipeline: the wire-format value (always a string when this applies) is parsed into an intermediate type before further conversion. Author-facing on Storage (`docs/reference.yaml`); also survives onto the Domain `TypeSpec.StringSpec` in some mapping paths, where it still means "parse this string into X" as a pipeline step, not a final representation.

**interpretedAs**:
Author-facing (Storage, `docs/reference.yaml`) annotation saying how the declared `type` should actually be treated — e.g. a `type: string` field that should be treated as a `long`. Combines with `parsedAs` when the value must first be parsed from a string before that reinterpretation applies.

**representedAs**:
Domain-only (not part of the Storage docs/reference.yaml). The final step of the serialization pipeline: after any `parsedAs` step, this is what the value should actually be represented as in the generated Kotlin code (e.g. an intermediate Int gets represented as a Boolean). Read by `codegen`'s `FieldAndTypeProcessor` to choose the generated type and serializer.

**Validation Context**:
The storage-layer, two-pass mechanism (`DocValidator.ValidationContext` and its subclasses) that gives `Validatable.validate()` implementations visibility beyond their own small window — specifically, whether a type-ref actually resolves to a real referenceable type. Pass 1 (`AccumulatingContext`) gathers the set of all referenceable type names while first deserializing the file; pass 2 (`FullContext`) carries that finished set plus the full Storage Document, and is what validators check type-refs against. Passed around untyped as `validationContext: Any?`, with implementers doing `is FullContext` checks.

**Domain Context**:
The `domain.Context` interface (`getTypeByName`), implemented by `domain.Document`, used to resolve a type name to a real `NamedType` on the resolved Domain model. Solves the same kind of problem as Validation Context (name resolution beyond local scope) but is structurally unrelated and operates one layer up, on the Domain model rather than the Storage model.
_Note_: `AccumulatingContext` is a reused class name across two unrelated things — the Validation Context pass-1 accumulator above, and a separate `domain.dsl.Document.AccumulatingContext` implementing Domain Context, in the DSL layer (an experimental way to define documentation in Kotlin/Kotlin script). Same name, different layer, intentional but worth knowing when you see it.
