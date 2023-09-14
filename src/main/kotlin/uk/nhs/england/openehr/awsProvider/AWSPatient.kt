package uk.nhs.england.openehr.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Organization
import org.hl7.fhir.r4.model.Patient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.configuration.FHIRServerProperties
import uk.nhs.england.openehr.configuration.MessageProperties
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class AWSPatient (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties,
                  val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun processQueryString(httpString: String?, nhsNumber : TokenParam? ) : String? {
        var queryString: String? = httpString
        if (queryString != null) {
            val params: List<String> = queryString.split("&")
            val newParams = mutableListOf<String>()
            var patient : Patient? = null
            if (nhsNumber != null) {
                if (nhsNumber.value == null || nhsNumber.system == null) throw UnprocessableEntityException("Malformed patient identifier parameter both system and value are required.")
                patient = get(Identifier().setSystem(nhsNumber.system).setValue(nhsNumber.value))
            }

            for (param in params) {
                val name: String = param.split("=").get(0)
                val value: String = param.split("=").get(1)
                if (patient != null && java.net.URLDecoder.decode(name, StandardCharsets.UTF_8.name()).equals("patient:identifier")) {
                    newParams.add( "patient=" + patient.idElement.idPart)
                } else if (name.equals("_content")) {
                    newParams.add("title=$value")
                } else if (name.equals("_total")) {
                    //newParams.add("title=$value")
                }
                else {
                    newParams.add(param)
                }
            }
            queryString = newParams.joinToString("&")
        }
        return queryString
    }
    public fun get(identifier: Identifier): Patient? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Patient::class.java)
                    .where(
                        Patient.IDENTIFIER.exactly()
                            .systemAndCode(identifier.system, identifier.value)
                    )
                    .returnBundle(Bundle::class.java)
                    .execute()
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle == null || !bundle.hasEntry()) return null
        return bundle.entryFirstRep.resource as Patient
    }

}
