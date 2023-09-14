package uk.nhs.england.openehr.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.*

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSObservation
import uk.nhs.england.openehr.awsProvider.AWSPatient
import uk.nhs.england.openehr.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class ObservationProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                           private val cognitoAuthInterceptor: CognitoAuthInterceptor,
                           var awsPatient: AWSPatient,
                           private val awsObservation: AWSObservation
) :IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */

    override fun getResourceType(): Class<Observation> {
        return Observation::class.java
    }
    companion object : KLogging()

    @Search
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = Observation.SP_PATIENT) patient : ReferenceParam?,
        @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        @OptionalParam(name = Observation.SP_DATE)  date : DateRangeParam?,
        @OptionalParam(name = Observation.SP_IDENTIFIER)  identifier : TokenParam?,
        @OptionalParam(name = Observation.SP_CODE) code : TokenOrListParam?,
        @OptionalParam(name = Observation.SP_CATEGORY)  category: TokenParam?,
        @OptionalParam(name = Observation.SP_RES_ID)  resid : StringParam?,
        @OptionalParam(name = Observation.SP_STATUS, )  status: TokenOrListParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_sort")  sort : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString, "Observation")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }




    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Observation? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"observation")
        return if (resource is Observation) resource else null
    }

}
