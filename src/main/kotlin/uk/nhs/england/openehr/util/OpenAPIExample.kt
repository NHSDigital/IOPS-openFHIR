package uk.nhs.england.openehr.util


import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.swagger.models.Xml
import io.swagger.v3.oas.models.media.XML
import org.apache.commons.io.IOUtils
import org.apache.xmlbeans.XmlException
import org.openehr.schemas.v1.TemplateDocument
import org.w3c.dom.Document
import org.w3c.dom.Node
import uk.nhs.england.openehr.model.openEHRtoFHIR
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory


class OpenAPIExample {
    public fun loadJSONExample(fileName :String): JsonNode {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fileName)

        val jsonStrings = inputStream.bufferedReader().readLines()
        var sb = StringBuilder()
        for (str in jsonStrings) sb.append(str)
        return ObjectMapper().readTree(sb.toString())

    }
    public fun loadXMLExample(fileName :String): Node? {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fileName)

        val document: TemplateDocument
        document = try {
            TemplateDocument.Factory.parse(
               inputStream
            )
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.template !== null) {
            return document.domNode
        }
        return null
    }

}
