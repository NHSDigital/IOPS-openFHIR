package uk.nhs.england.openehr.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.SortOrderEnum
import ca.uhn.fhir.rest.api.SortSpec
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.configuration.FHIRServerProperties
import uk.nhs.england.openehr.configuration.MessageProperties

import java.util.*

@Component
class AWSObservation(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                     @Qualifier("R4") val ctx: FhirContext,
                     val fhirServerProperties: FHIRServerProperties,
                     val awsPatient: AWSPatient,
                     val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")



    public fun get(identifier: Identifier): Observation? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Observation::class.java)
                    .where(
                        Observation.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Observation
    }




    public fun search(subject : Reference, coding: List<Coding>) : List<Observation> {
        var resources = mutableListOf<Observation>()
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Observation::class.java)
                    .where(
                        Observation.PATIENT.hasId(subject.reference.replace("Patient/",""))

                    )
                    .and(Observation.CODE.exactly().systemAndCode(coding[0].system,coding[0].code))
                    .sort(SortSpec().setParamName("date").setOrder(SortOrderEnum.DESC))
                    .returnBundle(Bundle::class.java)
                    .execute()
                break;
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle!=null && bundle.hasEntry()) {
            for (entry in bundle.entry) {
                if (entry.hasResource() && entry.resource is Observation) resources.add(entry.resource as Observation)
            }
        }
        return resources
    }
}
