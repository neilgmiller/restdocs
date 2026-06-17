package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.dsl.dataObject
import com.giffardtechnologies.restdocs.domain.dsl.document
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests that TypeRef fields nested inside inline ObjectSpec parameters in method requests
 * resolve to the correct Input variant (e.g. TimeRangeInput not TimeRange).
 *
 * Regression: createSubObjectClass was not propagating initializeWithDefault, causing
 * TypeRef resolution in nested inline objects to skip parameterContext = true.
 */
class MethodProcessorTypeRefTest {

    @TempDir
    lateinit var tempDir: File

    private val dtoPackage = "com.test.dto"
    private val requestsPackage = "com.test.requests"
    private val requestsDtoPackage = "com.test.requests.dto"

    @Test
    fun `inline ObjectSpec param containing TypeRef to ParameterOnly type uses FooInput`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("TimeRange") {
                add(Field(name = "fd", longName = "fromDateMS", type = TypeSpec.BasicSpec(DataType.LongType), isRequired = false))
                add(Field(name = "td", longName = "toDateMS", type = TypeSpec.BasicSpec(DataType.LongType), isRequired = false))
            })
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            Field(
                                name = "f",
                                longName = "filter",
                                type = TypeSpec.ObjectSpec(FieldElementList(Array.of(
                                    Field(name = "tr", longName = "timeRange", type = TypeSpec.TypeRefSpec("TimeRange", ctx), isRequired = false)
                                ))),
                                isRequired = false,
                            )
                        )
                    )
                )
            )
        }
        buildMethodProcessor(doc).processMethod(doc.service!!.methods.get(0))

        val content = requestFile("TestMethodRequest").readText()
        assertTrue(content.contains("TimeRangeInput"), "Inline param object should reference TimeRangeInput. Content:\n$content")
        assertFalse(content.contains(": TimeRange?"), "Inline param object should not reference plain TimeRange. Content:\n$content")
    }

    @Test
    fun `deeply nested inline ObjectSpec param containing TypeRef uses FooInput`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("TimeRange") {
                add(Field(name = "fd", longName = "fromDateMS", type = TypeSpec.BasicSpec(DataType.LongType), isRequired = false))
            })
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            Field(
                                name = "o",
                                longName = "options",
                                type = TypeSpec.ObjectSpec(FieldElementList(Array.of(
                                    Field(
                                        name = "f",
                                        longName = "filter",
                                        type = TypeSpec.ObjectSpec(FieldElementList(Array.of(
                                            Field(name = "tr", longName = "timeRange", type = TypeSpec.TypeRefSpec("TimeRange", ctx), isRequired = false)
                                        ))),
                                        isRequired = false,
                                    )
                                ))),
                                isRequired = false,
                            )
                        )
                    )
                )
            )
        }
        buildMethodProcessor(doc).processMethod(doc.service!!.methods.get(0))

        val content = requestFile("TestMethodRequest").readText()
        assertTrue(content.contains("TimeRangeInput"), "Deeply nested inline param object should reference TimeRangeInput. Content:\n$content")
        assertFalse(content.contains(": TimeRange?"), "Deeply nested inline param object should not reference plain TimeRange. Content:\n$content")
    }

    // -- helpers --

    private fun buildMethodProcessor(doc: com.giffardtechnologies.restdocs.domain.Document): MethodProcessor {
        val classifier = DataObjectUsageClassifier(doc)
        val fieldAndTypeProcessor = FieldAndTypeProcessor(
            objectPackage = dtoPackage,
            typeRefPackage = dtoPackage,
            classifier = classifier,
            requestsDtoPackage = requestsDtoPackage,
        )
        val enumProcessor = EnumProcessor(tempDir, dtoPackage)
        val bitSetProcessor = BitSetProcessor(tempDir, dtoPackage)
        return MethodProcessor(
            codeDirectory = tempDir,
            requestsPackage = requestsPackage,
            typeRefPackage = dtoPackage,
            fieldAndTypeProcessor = fieldAndTypeProcessor,
            enumProcessor = enumProcessor,
            bitSetProcessor = bitSetProcessor,
        )
    }

    private fun requestFile(name: String) = File(tempDir, requestsPackage.replace('.', '/') + "/$name.kt")

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
