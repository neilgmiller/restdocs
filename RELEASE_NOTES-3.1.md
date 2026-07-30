# Rest Docs 3.1 Release Notes

## New Features

- **Conditional sync/async response methods.** API methods can now declare a
  parameter that lets the caller choose, per request, whether the server
  returns the payload directly or an async job ID to poll. Previously a
  method could only be always-sync or always-async. Generated Swift clients
  expose separate `executeSync*` / `executeAsync*` methods for these
  endpoints, so callers never need to know about or set the control
  parameter themselves.

- **Structured deprecation for API methods.** `deprecated`, `deprecationNote`,
  and `deprecatedSince` are now first-class fields on a method, replacing
  free-text deprecation notices buried in descriptions. Deprecated methods
  are marked `@Deprecated` in generated Kotlin request classes and get a
  badge and note in the generated HTML reference docs.

- **Drop old deprecated methods from generated code.** The `kotlin_generator`
  now supports a `skipDeprecatedBefore.date` property to exclude deprecated
  methods older than a given date from generated client code, while keeping
  their entries (and validation/doc coverage) in `reference.yaml`.

- **`noPayload` flag for fire-and-forget responses.** Responses and async
  responses can now be explicitly marked as never returning a payload (e.g.
  fire-and-forget bulk jobs), distinguishing "intentionally no payload" from
  "not yet documented." This generates as `Unit` and is reflected in the
  HTML docs.

- **Scalar async response types now work correctly.** Declaring an
  `asyncResponse` as a scalar (e.g. `long`) previously validated without
  error but silently produced no fetch method and no useful documentation.
  Scalar async response types now generate a working fetch method and
  render correctly in the reference docs.

## Distribution Changes

- The generated HTML reference docs (`docs/reference.yaml` run through the
  doc generator) are now built and packaged automatically as part of every
  distribution, along with the standard doc template and `reference.yaml`
  itself — no manual steps required to keep the shipped docs current.
