# Open Issues

## Mapper TODOs (from Phase 01 review IN-03)

- [ ] **Sample values not mapped** — `StorageToDomainMappers.kt:190`
  `Field` domain builder block has no mapping for sample values. If the storage model ever gains a
  `sampleValues` field, it will need to be wired here.

- [ ] **Validate STRING→BOOLEAN spec approach** — `StorageToDomainMappers.kt:262`
  `parsedAs = BOOLEAN` on a STRING field produces `BooleanSpec(BooleanRepresentation.AsString)`,
  but it's unclear whether this is consistent with the `else` branch which delegates to
  `TypeSpec.StringSpec(parsedAs.mapToModel())`. Verify the two paths agree on the domain model
  shape for boolean-as-string fields.

- [ ] **Use a meaningful ID for response type spec** — `StorageToDomainMappers.kt:543`
  `ResponseStorageModel.mapToModel` passes the hardcoded string `"response"` as the type-spec
  identifier. A more useful ID would include the method name or endpoint path to give better
  context in validation error messages.
