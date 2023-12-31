package uk.nhs.england.openehr.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.nedap.archie.rm.composition.Composition
import com.nedap.archie.rm.composition.Observation
import com.nedap.archie.rm.datastructures.Element
import com.nedap.archie.rm.datastructures.PointEvent
import org.apache.commons.io.IOUtils
import org.ehrbase.serialisation.xmlencoding.CanonicalXML
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.IdType
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSQuestionnaire
import uk.nhs.england.openehr.transform.FHIRQuestionnaireResponseExtract
import uk.nhs.england.openehr.transform.FHIRQuestionnaireResponseToOpenEHRComposition
import uk.nhs.england.openehr.util.FhirSystems.*
import java.nio.charset.StandardCharsets
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
    fun convertToOpenEHRComposition(
        response: HttpServletResponse,
        @ResourceParam questionnaireResponse: QuestionnaireResponse
    ) {
        var composition = FHIRQuestionnaireResponseToOpenEHRComposition(fhirQuestionnaireResponseExtract, questionnaireResponse).convert()

        if (composition !== null) {
            val json = CanonicalXML().marshal(composition)
            response.setHeader("Content-Type","application/xml")
            response.writer.write(json)
        } else {
            throw UnprocessableEntityException("Questionnaire not found")
        }
    }

    @Operation(name = "\$convertComposition", idempotent = true, manualRequest = true, manualResponse = true)
    fun convertOpenEHRComposition(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
       val composition: Composition?
       try {
           val str = IOUtils.toString(request.getReader())
           composition = CanonicalXML().unmarshal(str, Composition::class.java);
           System.out.println(composition.name.value)

           for (content in composition.content) {
               System.out.println(content.javaClass.canonicalName)
                if (content is Observation) {
                    val obs = content as Observation
                    if (obs.data != null) {
                        for (event in obs.data.events) {
                            System.out.println("event " + event.javaClass.canonicalName)
                            System.out.println("event type " + event.javaClass.genericSuperclass.javaClass.canonicalName)
                            if (event is PointEvent) {
                                val pv = event as PointEvent
                                if (pv.data != null) {
                                    System.out.println(pv.data.javaClass)

                                }
                            }
                        }
                    }
                }
           }
       } catch (ex : Exception) {
           System.out.println(ex.message)
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
