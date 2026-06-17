package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.dsl.dataObject
import com.giffardtechnologies.restdocs.domain.dsl.document
import com.giffardtechnologies.restdocs.domain.dsl.namedEnumeration
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataObjectProcessorTest {

    @TempDir
    lateinit var tempDir: File

    private val dtoPackage = "com.test.dto"
    private val requestsDtoPackage = "com.test.requests.dto"

    // -- ResponseOnly --

    @Test
    fun `ResponseOnly generates Foo in dtoPackage`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(methods = Array.empty())
        }
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        assertTrue(dtoFile("Foo").exists())
    }

    @Test
    fun `ResponseOnly preserves constructor default for non-required field with default value`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {
                add(Field(name = "label", longName = "label", type = TypeSpec.BasicSpec(DataType.StringType), defaultValue = "hello", isRequired = false))
            })
            service = Service(methods = Array.empty())
        }
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = dtoFile("Foo").readText()
        assertTrue(content.contains("\"hello\""), "ResponseOnly Foo should retain the default value. Content:\n$content")
        assertFalse(content.contains("String?"), "ResponseOnly Foo field should not be nullable. Content:\n$content")
    }

    @Test
    fun `ResponseOnly does not generate FooInput`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(methods = Array.empty())
        }
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        assertFalse(requestsDtoFile("FooInput").exists())
    }

    // -- ParameterOnly --

    @Test
    fun `ParameterOnly generates FooInput in requestsDtoPackage`() {
        val doc = parameterOnlyDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        assertTrue(requestsDtoFile("FooInput").exists())
    }

    @Test
    fun `ParameterOnly does not generate Foo in dtoPackage`() {
        val doc = parameterOnlyDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        assertFalse(dtoFile("Foo").exists())
    }

    @Test
    fun `ParameterOnly FooInput suppresses constructor defaults`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Foo") {
                add(Field(name = "label", longName = "label", type = TypeSpec.BasicSpec(DataType.StringType), defaultValue = "hello", isRequired = false))
            })
            service = Service(
                methods = Array.of(
                    method(params = listOf(Field(name = "foo", longName = "foo", type = TypeSpec.TypeRefSpec("Foo", ctx))))
                )
            )
        }
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = requestsDtoFile("FooInput").readText()
        assertFalse(content.contains("\"hello\""), "ParameterOnly FooInput should not have specific default value. Content:\n$content")
        assertTrue(content.contains("String?"), "ParameterOnly FooInput field should be nullable. Content:\n$content")
    }

    // -- Mixed --

    @Test
    fun `Mixed generates Foo in dtoPackage with constructor defaults`() {
        val doc = mixedDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = dtoFile("Foo").readText()
        assertTrue(content.contains("\"hello\""), "Mixed Foo should retain the default value. Content:\n$content")
        assertFalse(content.contains("String?"), "Mixed Foo field should not be nullable. Content:\n$content")
    }

    @Test
    fun `Mixed generates FooInput in requestsDtoPackage`() {
        val doc = mixedDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        assertTrue(requestsDtoFile("FooInput").exists())
    }

    @Test
    fun `Mixed FooInput is named FooInput`() {
        val doc = mixedDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = requestsDtoFile("FooInput").readText()
        assertTrue(content.contains("data class FooInput"), "FooInput should declare data class FooInput. Content:\n$content")
    }

    @Test
    fun `Mixed FooInput suppresses constructor defaults`() {
        val doc = mixedDocument()
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = requestsDtoFile("FooInput").readText()
        assertFalse(content.contains("\"hello\""), "FooInput should not have specific default value. Content:\n$content")
        assertTrue(content.contains("String?"), "FooInput field should be nullable. Content:\n$content")
    }

    // -- TypeRef substitution --

    @Test
    fun `Mixed FooInput uses BarInput for a field typed as a Mixed Bar`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Bar") {
                add(Field(name = "value", longName = "value", type = TypeSpec.BasicSpec(DataType.StringType), isRequired = true))
            })
            addDataObject(dataObject("Foo") {
                add(Field(name = "bar", longName = "bar", type = TypeSpec.TypeRefSpec("Bar", ctx), isRequired = true))
            })
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            Field(name = "fooParam", longName = "fooParam", type = TypeSpec.TypeRefSpec("Foo", ctx)),
                            Field(name = "barParam", longName = "barParam", type = TypeSpec.TypeRefSpec("Bar", ctx)),
                        ),
                        response = Response(TypeSpec.TypeRefSpec("Foo", ctx)),
                    )
                )
            )
        }
        val processor = buildProcessor(doc)
        doc.dataObjects.forEach { processor.generateDataObjectClassFile(it) }

        val fooInputContent = requestsDtoFile("FooInput").readText()
        assertTrue(fooInputContent.contains("BarInput"), "FooInput field typed as Mixed Bar should reference BarInput. Content:\n$fooInputContent")
        assertFalse(fooInputContent.contains(": Bar\b".toRegex()), "FooInput should not reference plain Bar. Content:\n$fooInputContent")
    }

    @Test
    fun `Mixed Foo response variant uses Bar (not BarInput) for a field typed as a Mixed Bar`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Bar") {
                add(Field(name = "value", longName = "value", type = TypeSpec.BasicSpec(DataType.StringType), isRequired = true))
            })
            addDataObject(dataObject("Foo") {
                add(Field(name = "bar", longName = "bar", type = TypeSpec.TypeRefSpec("Bar", ctx), isRequired = true))
            })
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            Field(name = "fooParam", longName = "fooParam", type = TypeSpec.TypeRefSpec("Foo", ctx)),
                            Field(name = "barParam", longName = "barParam", type = TypeSpec.TypeRefSpec("Bar", ctx)),
                        ),
                        response = Response(TypeSpec.TypeRefSpec("Foo", ctx)),
                    )
                )
            )
        }
        val processor = buildProcessor(doc)
        doc.dataObjects.forEach { processor.generateDataObjectClassFile(it) }

        val fooContent = dtoFile("Foo").readText()
        assertTrue(fooContent.contains("Bar"), "Foo response variant field should reference Bar. Content:\n$fooContent")
        assertFalse(fooContent.contains("BarInput"), "Foo response variant should not reference BarInput. Content:\n$fooContent")
    }

    @Test
    fun `ParameterOnly FooInput uses original enum name (not EnumInput) for an enum-typed field`() {
        val archiveFilter = namedEnumeration("ArchiveFilter", DataType.IntType) {
            value(0, "include_all")
            value(1, "include_only_archive")
        }
        val doc = document("test") {
            val ctx = context
            addNamedEnumeration(archiveFilter)
            addDataObject(dataObject("Filter") {
                add(Field(name = "af", longName = "archiveFilter", type = TypeSpec.TypeRefSpec("ArchiveFilter", ctx), isRequired = false))
            })
            service = Service(
                methods = Array.of(
                    method(params = listOf(Field(name = "filter", longName = "filter", type = TypeSpec.TypeRefSpec("Filter", ctx))))
                )
            )
        }
        buildProcessor(doc).generateDataObjectClassFile(doc.dataObjects.first())

        val content = requestsDtoFile("FilterInput").readText()
        assertTrue(content.contains("ArchiveFilter"), "FilterInput should reference ArchiveFilter. Content:\n$content")
        assertFalse(content.contains("ArchiveFilterInput"), "FilterInput must not reference ArchiveFilterInput. Content:\n$content")
    }

    @Test
    fun `ParameterOnly FooInput uses BarInput for a field typed as a ParameterOnly Bar`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Bar") {
                add(Field(name = "value", longName = "value", type = TypeSpec.BasicSpec(DataType.StringType), isRequired = true))
            })
            addDataObject(dataObject("Foo") {
                add(Field(name = "bar", longName = "bar", type = TypeSpec.TypeRefSpec("Bar", ctx), isRequired = true))
            })
            // Both Bar and Foo are ParameterOnly
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            Field(name = "fooParam", longName = "fooParam", type = TypeSpec.TypeRefSpec("Foo", ctx)),
                            Field(name = "barParam", longName = "barParam", type = TypeSpec.TypeRefSpec("Bar", ctx)),
                        ),
                    )
                )
            )
        }
        val processor = buildProcessor(doc)
        doc.dataObjects.forEach { processor.generateDataObjectClassFile(it) }

        val fooInputContent = requestsDtoFile("FooInput").readText()
        assertTrue(fooInputContent.contains("BarInput"), "ParameterOnly FooInput field typed as ParameterOnly Bar should reference BarInput. Content:\n$fooInputContent")
    }

    // -- helpers --

    private fun buildProcessor(doc: com.giffardtechnologies.restdocs.domain.Document): DataObjectProcessor {
        val enumProcessor = EnumProcessor(tempDir, dtoPackage)
        val bitSetProcessor = BitSetProcessor(tempDir, dtoPackage)
        val classifier = DataObjectUsageClassifier(doc)
        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage, classifier = classifier, requestsDtoPackage = requestsDtoPackage)
        val objectProcessor = ObjectProcessor(tempDir, fieldAndTypeProcessor, enumProcessor, bitSetProcessor)
        return DataObjectProcessor(dtoPackage, requestsDtoPackage, objectProcessor, classifier)
    }

    private fun dtoFile(name: String) = File(tempDir, dtoPackage.replace('.', '/') + "/$name.kt")
    private fun requestsDtoFile(name: String) = File(tempDir, requestsDtoPackage.replace('.', '/') + "/$name.kt")

    private fun parameterOnlyDocument() = document("test") {
        val ctx = context
        addDataObject(dataObject("Foo") {})
        service = Service(
            methods = Array.of(
                method(params = listOf(Field(name = "foo", longName = "foo", type = TypeSpec.TypeRefSpec("Foo", ctx))))
            )
        )
    }

    private fun mixedDocument() = document("test") {
        val ctx = context
        addDataObject(dataObject("Foo") {
            add(Field(name = "label", longName = "label", type = TypeSpec.BasicSpec(DataType.StringType), defaultValue = "hello", isRequired = false))
        })
        service = Service(
            methods = Array.of(
                method(
                    params = listOf(Field(name = "fooParam", longName = "fooParam", type = TypeSpec.TypeRefSpec("Foo", ctx))),
                    response = Response(TypeSpec.TypeRefSpec("Foo", ctx)),
                )
            )
        )
    }

    private fun method(
        params: List<Field> = emptyList(),
        response: Response? = null,
    ) = Method(
        method = Method.HTTPMethod.POST,
        id = 1,
        name = "testMethod",
        isAuthenticationRequired = false,
        parameterElementList = FieldElementList(Array.ofAll(params)),
        response = response,
    )
}
