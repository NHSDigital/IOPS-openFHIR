package uk.nhs.england.openehr.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import io.swagger.util.Yaml
import io.swagger.v3.core.util.Json
import mu.KLogging
import org.apache.commons.io.IOUtils
import org.apache.xmlbeans.XmlException
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent
import org.openehr.schemas.v1.ArchetypeDocument
import org.openehr.schemas.v1.TemplateDocument
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSPatient
import uk.nhs.england.openehr.awsProvider.AWSQuestionnaire
import uk.nhs.england.openehr.interceptor.CognitoAuthInterceptor
import uk.nhs.england.openehr.model.openEHRtoFHIR
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class QuestionnaireProvider (@Qualifier("R4") private val fhirContext: FhirContext,
    private val codeSystem: List<CodeSystem>,
    private val awsQuestionnaire: AWSQuestionnaire,
    private val awsPatient: AWSPatient,
    private val cognitoAuthInterceptor: CognitoAuthInterceptor
) :IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */

    override fun getResourceType(): Class<Questionnaire> {
        return Questionnaire::class.java
    }

    companion object : KLogging()

    @Operation(name = "\$convertArchetype", manualRequest = true, idempotent = true)
    fun convertArchetype(request : HttpServletRequest
    ): Questionnaire? {
        val document: ArchetypeDocument
        document = try {
            ArchetypeDocument.Factory.parse(ByteArrayInputStream(IOUtils.toString(request.getReader()).toByteArray(StandardCharsets.UTF_8)))
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.archetype !== null) {
            val archetype = document.archetype
            val openEHRtoFHIR = openEHRtoFHIR(document.archetype, this.codeSystem)
            return openEHRtoFHIR.questionnaire
        }
        return null
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam questionnaire: Questionnaire): MethodOutcome? {
        return awsQuestionnaire.create(questionnaire)
    }

    @Search
    fun search(
        httpRequest: HttpServletRequest,
        @OptionalParam(name = Questionnaire.SP_CODE) code: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_URL) url: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_CONTEXT) context: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_DATE) date: DateParam?,
        @OptionalParam(name = Questionnaire.SP_IDENTIFIER) identifier: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_PUBLISHER) publisher: StringParam?,
        @OptionalParam(name = Questionnaire.SP_STATUS) status: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_TITLE) title: StringParam?,
        @OptionalParam(name = Questionnaire.SP_VERSION) version: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_DEFINITION) definition: TokenParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,null)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"Questionnaire")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }



    //, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/QuestionnaireResponse-extract"
    @Operation(name = "\$convertTemplate", manualRequest = true, idempotent = true)
    fun convertTemplate(request : HttpServletRequest
    ): Questionnaire? {
        val document: TemplateDocument
        document = try {
            TemplateDocument.Factory.parse(ByteArrayInputStream(IOUtils.toString(request.getReader()).toByteArray(StandardCharsets.UTF_8)))
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.template !== null) {
            val template = document.template
            val openEHRtoFHIR = openEHRtoFHIR(document.template, this.codeSystem)
            return openEHRtoFHIR.questionnaire
        }
        return null
    }

}
