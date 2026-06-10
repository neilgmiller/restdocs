# Retrospective

## Milestone: v1.0 — asyncResponse Block Support

**Shipped:** 2026-06-09
**Phases:** 3 | **Plans:** 5

### What Was Built

- Storage/domain/mapper pipeline extended with nullable `asyncResponse` field — Jackson binds it without error
- `(String)->Unit` warningEmitter channel threaded through the full Jackson validation stack for VALID-03/04
- Cross-field validation: warns on undocumented async payloads, errors on invalid async declarations
- `kotlin_generator` emits typed `*AsyncResponse` data classes for methods with inline async payloads
- `rest_api_doc.vm` (canonical live template) renders "Async Response (via getAsyncJobStatus)" section with field table, prose note, and typeRef link branch

### What Worked

- **Reusing existing types verbatim**: `asyncResponse` uses the existing `Response` type throughout — no new types, no new mapper logic, just an extra field. Zero friction at every layer.
- **Cross-field validation gating on context type**: VALID-03/04 checking `validationContext !is AccumulatingContext` was the right call — avoids false positives on the first pass without any special-casing.
- **Phased dependency ordering**: Phases 2 and 3 were independent after Phase 1, which meant the storage/domain foundation was solid before any output-layer work began.
- **Code review driving quality**: The post-phase code reviews surfaced real issues (stub template divergence, unused imports, weak test assertions) before merge.

### What Was Inefficient

- **Stub template vs live template**: Phase 3 started with a minimal stub Velocity template that had already diverged from the production template (wrong loop variable `$resource` vs `$method`, missing sidebar). Significant mid-phase rework to replace it with the canonical live template from allego1.0. Should have confirmed the live template path earlier.
- **REQUIREMENTS.md traceability not updated during phases**: The traceability table still showed "Pending" for all requirements at milestone close — it wasn't maintained during execution. The phase SUMMARY files were the authoritative record.

### Patterns Established

- **`responseIsAsync(Response)`** on `ValidationContext` with `asyncCapableResponseTypes` set — clean extension point for future async-capable type additions
- **`substringAfter(id="methodName")` + `substringBefore("<h4 ")` for section-bounded HTML assertions** — robust pattern that survives method reordering and additions
- **Test fixture method naming**: Case 1 (pure async), Case 2 (mixed), Case 3 (sync/regression), Case 4 (typeRef) — clear coverage taxonomy

### Key Lessons

- Confirm which template file is the actual production template before writing test scaffolding that loads it — `DocGeneratorCommand.kt:52` defaults to `File(user.dir, "rest_api_doc.vm")`, not `docs/rest_api_doc.vm`
- When adding a new YAML field to a Jackson model, the Jackson `FAIL_ON_UNKNOWN_PROPERTIES = true` constraint means the storage field IS the validator whitelist — no separate validator change needed
- GSD phase reviews are worth doing even for small phases; the Phase 3 review caught the template divergence before it caused production issues

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Duration | Rework? |
|-----------|--------|-------|----------|---------|
| v1.0 asyncResponse | 3 | 5 | 7 days | Phase 3 template replacement |
