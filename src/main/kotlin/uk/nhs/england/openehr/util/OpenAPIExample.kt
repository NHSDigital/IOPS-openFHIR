package uk.nhs.england.openehr.util


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.w3c.dom.Node


class OpenAPIExample {
    public fun loadJSONExample(fileName :String): JsonNode {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fileName)

        val jsonStrings = inputStream.bufferedReader().readLines()
        var sb = StringBuilder()
        for (str in jsonStrings) sb.append(str)
        return ObjectMapper().readTree(sb.toString())

    }
    public fun loadXMLExampleAsJson(fileName :String): JsonNode {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fileName)

        val xmlMapper = XmlMapper()
        val node: JsonNode = xmlMapper.readTree(inputStream)
        //val jsonMapper = ObjectMapper()
        //val json = jsonMapper.writeValueAsString(node)
        return node
    }
    public fun loadXMLExampleAsXml(fileName :String): XmlMapper {

        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fileName)

        val xmlMapper = XmlMapper()
        val node = xmlMapper.readTree(inputStream)

        return xmlMapper
    }

}
