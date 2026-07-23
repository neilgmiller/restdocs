# Storage Response inherits TypeSpec; Domain Response wraps it

`storage.Response` extends `storage.TypeSpec` because that mirrors how an API author writes a response inline in the YAML spec — as type fields directly on the response, not as a nested object. `domain.Response` deliberately switches to composition, wrapping a `TypeSpec` alongside `description` and `noPayload`, because that's the better shape for the resolved model that every generator consumes. The asymmetry between the two Response representations is intentional, not accidental drift.
