# History: `noPayload`/`deprecated` Proposals

Rejected approaches considered on the way to the current proposals. See
[nopayload-proposal.md](completed/nopayload-proposal.md) and [deprecated-proposal.md](docs/proposals/deprecated-proposal.md)
for the accepted designs.

---

## Rejected: `none` as a `DataType` enum value

An earlier draft considered adding `none` as a new `DataType` enum value
(`docs/reference.yaml:21-50`), usable anywhere a `TypeSpec`/`Response` accepts a `type`:

```yaml
- value: none
  description: No value is ever returned for this field.
```

Rejected: `DataType` is shared across every `TypeSpec`/`Response` context — `Field`, nested
`object` members, `response`, `asyncResponse` — and "no value" is only ever meaningful on
`asyncResponse`. Adding it to the shared enum risks it creeping into contexts (e.g. a `Field`
inside an `object`) where "this field has no type" isn't a coherent statement.

Replaced by: a `noPayload` boolean field on `Response` (covering both `response` and
`asyncResponse`, but not `Field`/nested `object` members) — see
[nopayload-proposal.md](completed/nopayload-proposal.md).
