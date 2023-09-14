package uk.nhs.england.openehr

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.england.openehr.configuration.FHIRServerProperties
import uk.nhs.england.openehr.configuration.MessageProperties
import uk.nhs.england.openehr.interceptor.AWSAuditEventLoggingInterceptor
import uk.nhs.england.openehr.interceptor.CapabilityStatementInterceptor
import uk.nhs.england.openehr.provider.CodeSystemProvider
import uk.nhs.england.openehr.provider.ObservationProvider
import uk.nhs.england.openehr.provider.QuestionnaireProvider
import uk.nhs.england.openehr.provider.QuestionnaireResponseProvider

import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/openFHIR/R4/*", loadOnStartup = 1, displayName = "openFHIR")
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    public val fhirServerProperties: FHIRServerProperties,
    val messageProperties: MessageProperties,
    val sqs : AmazonSQS,
    val questionnaireProvider: QuestionnaireProvider,
    val questionnaireResponseProvider: QuestionnaireResponseProvider,
    val codeSystemProvider: CodeSystemProvider,
    val observationProvider: ObservationProvider

    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(questionnaireProvider)
        registerProvider(questionnaireResponseProvider)
        registerProvider(observationProvider)
        registerProvider(codeSystemProvider)

        registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,fhirServerProperties))


        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties,
                messageProperties,
                sqs
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)


        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
