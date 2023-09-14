package uk.nhs.england.openehr.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets


@Component
class AWSQuestionnaire(val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                       @Qualifier("R4") val ctx: FhirContext,
                       val awsAuditEvent: AWSAuditEvent
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun search(uriParam: UriParam?): List<Questionnaire>? {
        var awsBundle: Bundle? = null
        val list = mutableListOf<Questionnaire>()

        // if (uriParam == null || uriParam.value == null) throw UnprocessableEntityException("url parameter is mandatory")
        var retry = 3
        while (retry > 0) {
            try {
                if (uriParam == null || uriParam.value == null) {
                    awsBundle = awsClient.search<IBaseBundle>().forResource(Questionnaire::class.java)
                        .returnBundle(Bundle::class.java)
                        .execute()
                } else {
                    val decodeUrl = java.net.URLDecoder.decode(uriParam.value, StandardCharsets.UTF_8.name())
                    awsBundle = awsClient.search<IBaseBundle>().forResource(Questionnaire::class.java)
                        .where(
                            Questionnaire.URL.matches().value(decodeUrl)
                        )
                        .returnBundle(Bundle::class.java)
                        .execute()
                }
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (awsBundle != null) {
            if (awsBundle.hasEntry() ) {
                for (entry in awsBundle.entry) {
                    if (entry.hasResource() && entry.resource is Questionnaire) {
                        list.add(entry.resource as Questionnaire)
                    }
                }
            }
        }
        return list
    }

    fun update(questionnaire: Questionnaire, theId: IdType): MethodOutcome? {
        var response: MethodOutcome? = null
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .update()
                    .resource(questionnaire)
                    .withId(theId)
                    .execute()
                val storedQuestionnaire = response.resource as Questionnaire
                val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.U)
                awsAuditEvent.writeAWS(auditEvent)
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }

    fun create(questionnaire: Questionnaire): MethodOutcome? {
        if (questionnaire.hasCode() && !questionnaire.hasIdentifier()) {
            questionnaire.identifier.add(Identifier()
                .setSystem(questionnaire.codeFirstRep.system)
                .setValue(questionnaire.codeFirstRep.code))
        }
        if (questionnaire.hasCode() && questionnaire.codeFirstRep.hasCode() && !questionnaire.hasUrl()) {
            questionnaire.url = "https://example.fhir.nhs.uk/Questionnaire/"+questionnaire.codeFirstRep.code
        }
        if (!questionnaire.hasUrl()) throw UnprocessableEntityException("A Questionnaire.code or Questionnaire.url is required")
        val duplicateCheck = search(UriParam().setValue(questionnaire.url))
        if (duplicateCheck != null && duplicateCheck.isNotEmpty()) {
            questionnaire.id = duplicateCheck[0].id
            return update(questionnaire,questionnaire.idElement)
        } else {

            var response: MethodOutcome? = null
            var retry = 3
            while (retry > 0) {
                try {
                    response = awsClient
                        .create()
                        .resource(questionnaire)
                        .execute()
                    val storedQuestionnaire = response.resource as Questionnaire
                    val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.C)
                    awsAuditEvent.writeAWS(auditEvent)
                    break
                } catch (ex: Exception) {
                    // do nothing
                    log.error(ex.message)
                    retry--
                    if (retry == 0) throw ex
                }
            }
            return response
        }
    }

    fun delete(theId: IdType): MethodOutcome? {
        var response: MethodOutcome? = null
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .delete()
                    .resourceById(theId)
                    .execute()

                /*
                val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.D)
                awsAuditEvent.writeAWS(auditEvent)
                */
                break

            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }


    fun read(theId: IdType): MethodOutcome? {
        var response = MethodOutcome()
        var retry = 3
        while (retry > 0) {
            try {
                val result = awsClient
                    .read()
                    .resource(Questionnaire::class.java)
                    .withId(theId)
                    .execute()
                if (result != null) {
                    response.setResource(result)
                }
                break

            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }
}
