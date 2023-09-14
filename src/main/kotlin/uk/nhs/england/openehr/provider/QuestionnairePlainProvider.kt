package uk.nhs.england.openehr.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSPatient
import uk.nhs.england.openehr.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class QuestionnairePlainProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                                  var awsPatient: AWSPatient,
                                  private val cognitoAuthInterceptor: CognitoAuthInterceptor
)  {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */


    companion object : KLogging()



}
