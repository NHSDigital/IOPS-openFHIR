package uk.nhs.england.openehr.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.ehrbase.serialisation.xmlencoding.CanonicalXML
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSQuestionnaire
import uk.nhs.england.openehr.transform.FHIRQuestionnaireResponseExtract
import uk.nhs.england.openehr.transform.FHIRQuestionnaireResponseToOpenEHRComposition
import uk.nhs.england.openehr.util.FhirSystems.*
import java.util.*


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class QuestionnaireResponseProvider(

    var awsQuestionnaire: AWSQuestionnaire
) : IResourceProvider {
    override fun getResourceType(): Class<QuestionnaireResponse> {
        return QuestionnaireResponse::class.java
    }
    val fhirQuestionnaireResponseExtract : FHIRQuestionnaireResponseExtract

    init  {
        fhirQuestionnaireResponseExtract = FHIRQuestionnaireResponseExtract(awsQuestionnaire)
    }

    @Operation(name = "\$convertToComposition", idempotent = true, manualResponse = true)
    fun convertOpenEHRComposition(
        response: HttpServletResponse,
        @ResourceParam questionnaireResponse: QuestionnaireResponse
    ) {
        var composition = FHIRQuestionnaireResponseToOpenEHRComposition(fhirQuestionnaireResponseExtract).convert(questionnaireResponse)

        if (composition !== null) {
            val json = CanonicalXML().marshal(composition)
            response.setHeader("Content-Type","application/xml")
            response.writer.write(json)
        } else {
            throw UnprocessableEntityException("Questionnaire not found")
        }
    }



    fun getQuestionnaire(questionnaireResponse: QuestionnaireResponse) : Questionnaire? {
        var questionnaire : Questionnaire? = null
        var questionnaireList = awsQuestionnaire.search(UriParam().setValue(questionnaireResponse?.questionnaire))
        if (questionnaireList == null || questionnaireList.size==0) {
            var result = awsQuestionnaire.read(IdType().setValue(questionnaireResponse.questionnaire))
            if (result !== null && result.resource !== null) {
                questionnaire = result.resource as Questionnaire
            }
        } else {
            questionnaire = questionnaireList[0]
        }
        return  questionnaire
    }

    @Operation(name = "\$extract", idempotent = true, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/QuestionnaireResponse-extract")
    fun expand(@ResourceParam questionnaireResponse: QuestionnaireResponse
    ): Bundle {
        return fhirQuestionnaireResponseExtract.extract(questionnaireResponse)
    }

}
