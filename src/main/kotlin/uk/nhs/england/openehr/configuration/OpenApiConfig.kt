package uk.nhs.england.openehr.configuration

import ca.uhn.fhir.context.FhirContext
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.nhs.england.openehr.util.OpenAPIExample

@Configuration
class OpenApiConfig(@Qualifier("R4") val ctx : FhirContext) {


    var FORMS = "Structured Data Capture"
    var TERMINOLOGY = "Terminology"
    var CONFORMANCE = "Conformance"

    @Bean
    fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

            val oas = OpenAPI()
            .info(
                Info()
                    .title(fhirServerProperties.server.name)
                    .version(fhirServerProperties.server.version)
                    .description(
                    ""        )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )

        oas.addServersItem(
            Server().description(fhirServerProperties.server.name).url(fhirServerProperties.server.baseUrl)
        )

        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(FORMS)
                .description("[HL7 FHIR Structured Data Capture](http://hl7.org/fhir/uv/sdc/) \n"
                )
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(TERMINOLOGY)
                .description("[HL7 FHIR R4 Terminology](https://hl7.org/fhir/R4/terminology-module.html) \n"
                    + "[openEHR Terminology](https://specifications.openehr.org/releases/TERM/latest) \n"
                )
        )



        oas.path("/openFHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(CONFORMANCE)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))

        // ITI 96 Query Code System

        val pathItem = getPathItem(TERMINOLOGY,"CodeSystem", "Code System", "url", "https://specifications.openehr.org/fhir/codesystem-property",
            "This transaction is used by the Terminology Consumer to solicit information about code systems " +
                    "whose data match data provided in the query parameters on the request message. The request is " +
                    "received by the Terminology Repository. The Terminology Repository processes the request and " +
                    "returns a response of the matching code systems.")
        oas.path("/openFHIR/R4/CodeSystem",pathItem)

        val codeSystemItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(TERMINOLOGY)
                    .summary("Read CodeSystem by id")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The id of the CodeSystem")
                        .schema(StringSchema())
                        .example("codesystem-property"))
            )
        oas.path("/openFHIR/R4/CodeSystem/{id}",codeSystemItem)


        // QuestionnaireResponse

        val examplesQuestionnaireResponseExtract = LinkedHashMap<String,Example?>()

        val questionnaireResponseExtractItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FORMS)
                    .summary("Form Data Extraction")
                    .description("[Form Data Extraction](http://hl7.org/fhir/uv/sdc/extraction.html) Allows data captured in a QuestionnaireResponse to be extracted and used to create or update other FHIR resources - allowing the data to be more easily searched, compared and used by other FHIR systems")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaireResponseExtract )
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))



        oas.path("/openFHIR/R4/QuestionnaireResponse/\$extract",questionnaireResponseExtractItem)


        // Questionnaire

        val examplesQuestionnaireConvertTemplate = LinkedHashMap<String,Example?>()
/*
        examplesQuestionnaireConvertTemplate.put("IDCR - Vital Signs Encounter.v1 https://ckm.apperta.org/ckm/templates/1051.57.141",
            Example().value(OpenAPIExample().loadXMLExample("Template/" + "IDCR - Vital Signs Encounter.v1.xml"))
        )
        examplesQuestionnaireConvertTemplate.put("ReSPECT-3.v0 (DHCW or NHS Scotland?) https://ckm.apperta.org/ckm/templates/1051.57.279",
            Example().value(OpenAPIExample().loadXMLExample("Template/" + "ReSPECT-3.v0.xml"))
        )
*/
        val questionnaireConvertTemplate = PathItem()
            .post(
                Operation()
                    .addTagsItem(FORMS)
                    .summary("Converts an openEHR operational template to a FHIR Questionnaire")
                    .description("Requires an operational template [Form Data Extraction](https://specifications.openehr.org/releases/AM/Release-2.2.0/Overview.html#_archetype_technology_overview)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/xml",
                            MediaType()
                                .examples(examplesQuestionnaireConvertTemplate)
                                .schema(StringSchema())
                        )
                    )))



        oas.path("/openFHIR/R4/Questionnaire/\$convertTemplate",questionnaireConvertTemplate)

        val questionnaireConvertArchetype = PathItem()
            .post(
                Operation()
                    .addTagsItem(FORMS)
                    .summary("Converts an openEHR archetype to a FHIR Questionnaire")
                    .description("Requires an archetype [Form Data Extraction](https://specifications.openehr.org/releases/AM/Release-2.2.0/Overview.html#_archetype_technology_overview)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))

        oas.path("/openFHIR/R4/Questionnaire/\$convertArchetype",questionnaireConvertArchetype)

        val examplesQuestionnaire = LinkedHashMap<String,Example?>()

        examplesQuestionnaire["IDCR-VitalSignsEncounter.v1"] =
            Example().value(OpenAPIExample().loadJSONExample("Questionnaire/IDCR-VitalSignsEncounter.v1.json"))

        oas.path("/openFHIR/R4/Questionnaire",
            PathItem().
            get(Operation()
                .addTagsItem(FORMS)
                .summary("Finding a Questionnaire")
                .description("[Finding a Questionnaire](http://hl7.org/fhir/uv/sdc/search.html) Before a questionnaire can be filled out, it must first be 'found'. In some cases, workflow will dictate the specific Questionnaire to use - it will be pointed to by a Task to be performed, be included in a CarePlan, referenced by a PlanDefinition or made available in some other way. However, often users will need to search a registry or other repository to find the desired form, clinical instrument, etc. This portion of the SDC specification sets expectations for systems that support storing questionnaires and allowing client systems to search against their repository of questionnaires to find those that meet specified criteria. ")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("url")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The uri that identifies the Questionnaire ")
                    .schema(StringSchema().format("token"))
                    .example("https://fhir.openehr.example.org/Questionnaire/IDCR-VitalSignsEncounter.v1"))
                .addParametersItem(Parameter()
                    .name("code")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("A code that corresponds to one of its items in the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("context")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("A use context assigned to the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("date")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The questionnaire publication date")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("identifier")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("External identifier for the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("publisher")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("Name of the publisher of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("status")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The current status of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("title")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The human-friendly name of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("version")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The business version of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("definition")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("ElementDefinition - details for the item")
                    .schema(StringSchema()))
            )
                .post(Operation()
                    .addTagsItem(FORMS)
                    .summary("Add Questionnaire (wil perform update if the Questionnaire exists - this may be removed)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaire )
                                .schema(StringSchema()))
                    )))
        )


        return oas
    }

    fun getApiResponses(): ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content =
            Content().addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
                .addMediaType("application/fhir+xml", MediaType().schema(StringSchema()._default("")))
        return ApiResponses().addApiResponse("200", response200)
    }
    fun getApiResponsesBinary() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content()
            .addMediaType("*/*", MediaType().schema(StringSchema()._default("{}")))
            .addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
            .addMediaType("application/fhir+xml", MediaType().schema(StringSchema()._default("<>")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }
    fun getPathItem(tag :String, name : String,fullName : String, param : String, example : String, description : String ) : PathItem {
        val pathItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(tag)
                    .summary("search-type")
                    .description(description)
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name(param)
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the "+fullName)
                        .schema(StringSchema().format("token"))
                        .example(example)))
        return pathItem
    }
}
