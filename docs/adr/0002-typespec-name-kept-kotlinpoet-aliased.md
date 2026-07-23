# Keep TypeSpec name; alias KotlinPoet's TypeSpec as KpTypeSpec

`TypeSpec` (in both `storage` and `domain`) predates the project's adoption of KotlinPoet, whose own `com.squareup.kotlinpoet.TypeSpec` shares the bare name and collides in `codegen/`, the one package where both are in scope simultaneously. Rather than rename our `TypeSpec` — which is an accurate name for what it models — the codebase keeps it and instead aliases KotlinPoet's import as `KpTypeSpec` wherever both are used in the same file.
