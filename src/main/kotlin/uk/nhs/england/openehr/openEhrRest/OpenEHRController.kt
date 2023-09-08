package uk.nhs.england.openehr.openEhrRest

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import io.swagger.v3.oas.annotations.Hidden
import mu.KLogging
import org.apache.xmlbeans.XmlException
import org.hl7.fhir.r4.model.ContactDetail
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Questionnaire
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import org.openehr.schemas.v1.TemplateDocument
import uk.nhs.england.openehr.util.FhirSystems


@RestController
@Hidden
class OpenEHRController(
    @Qualifier("R4") private val fhirContext: FhirContext,
) {
    companion object : KLogging()


    @PostMapping("/openehr/\$convertOpenEHRTemplate",produces = ["application/json","application/xml"])
    fun convert(
        @RequestBody templateXML: String,

    ): String {
        var questionnaire = Questionnaire()

        val document: TemplateDocument
        document = try {
            TemplateDocument.Factory.parse(ByteArrayInputStream(templateXML.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.template !== null) {
            var template = document.template
            if (template.templateId !== null) {
                questionnaire.url = "https://example.fhir.openehr.org/Questionnaire/"+template.templateId.value
                questionnaire.url = questionnaire.url.replace(" ","")
            }
            if (template.uid !== null) {
                questionnaire.identifier.add(Identifier().setValue(template.uid.value))
            }
            if (template.description !== null) {
                if (template.description.detailsArray !== null) {
                    questionnaire.purpose = template.description.detailsArray[0].purpose
                }
                if (template.description.originalAuthorArray !== null && template.description.originalAuthorArray.size>0) {
                    for (author in template.description.originalAuthorArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.stringValue))
                    }
                }
                if (template.description.otherContributorsArray !== null && template.description.otherContributorsArray.size>0) {
                    for (author in template.description.otherContributorsArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.toString()).)
                    }
                }
            }
        }
        return fhirContext.newJsonParser().encodeResourceToString(questionnaire)
    }

}
