package uk.nhs.england.openehr.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.configuration.FHIRServerProperties
import uk.nhs.england.openehr.configuration.MessageProperties
import uk.nhs.england.openehr.util.FhirSystems
import java.util.*

@Component
class AWSAuditEvent(@Qualifier("R4") val ctx: FhirContext,
                    val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                    val fhirServerProperties: FHIRServerProperties
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun writeAWS(event: AuditEvent) {
        val audit = ctx!!.newJsonParser().encodeResourceToString(event)
        if (event.hasOutcome() && event.outcome != AuditEvent.AuditEventOutcome._0) {
            log.error(audit)
        } else {
            log.info(audit)
        }
        /* Add back in at later date
        val send_msg_request = SendMessageRequest()
            .withQueueUrl(sqs.getQueueUrl(MessageProperties.getAwsQueueName()).getQueueUrl())
            .withMessageBody(audit)
            .withDelaySeconds(5)
        sqs!!.sendMessage(send_msg_request)

         */
    }

    fun createAudit(resource : Resource, auditEventAction: AuditEvent.AuditEventAction?): AuditEvent {
        val auditEvent = AuditEvent()
        auditEvent.recorded = Date()
        auditEvent.action = auditEventAction

        // Entity
        val entityComponent = auditEvent.addEntity()
        var path: String? = messageProperties.getCdrFhirServer()
        path += "/"+resource.javaClass.simpleName
        // May have exceptions to the above
        auditEvent.outcome = AuditEvent.AuditEventOutcome._0
        entityComponent.addDetail().setType("query").value = StringType(path)
        auditEvent.type = Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("transmit")
        entityComponent.addDetail().setType("resource").value = StringType(resource.javaClass.simpleName)
        entityComponent.type = Coding().setSystem(FhirSystems.FHIR_RESOURCE_TYPE).setCode("Patient")
        auditEvent.source.observer = Reference()
            .setIdentifier(Identifier().setValue(fhirServerProperties.server.baseUrl))
            .setDisplay((fhirServerProperties.server.name + " " + fhirServerProperties.server.version).toString() + " " + fhirServerProperties.server.baseUrl)
            .setType("Device")


        // Agent Application
        val agentComponent = auditEvent.addAgent()
        agentComponent.requestor = true
        agentComponent.type = CodeableConcept(Coding().setSystem(FhirSystems.DICOM_AUDIT_ROLES).setCode("110150"))

        /// Agent Patient about
        val patientAgent = auditEvent.addAgent()
        patientAgent.requestor = false
        patientAgent.type =
            CodeableConcept(Coding().setSystem(FhirSystems.V3_ROLE_CLASS).setCode("PAT"))
        patientAgent.who = Reference().setType("Patient")
            .setReference(resource.javaClass.simpleName+"/" + resource.idElement.idPart)
        return auditEvent
    }

}
