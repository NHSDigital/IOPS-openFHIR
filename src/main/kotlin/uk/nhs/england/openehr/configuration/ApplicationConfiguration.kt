package uk.nhs.england.openehr.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import ca.uhn.fhir.rest.client.api.IGenericClient
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.AmazonSQSException
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.io.IOUtils
import org.hl7.fhir.r4.model.CodeSystem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestTemplate
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import uk.nhs.england.openehr.interceptor.CognitoAuthInterceptor
import uk.nhs.england.openehr.util.CorsFilter
import javax.servlet.Filter


@Configuration
open class ApplicationConfiguration(val messageProperties: MessageProperties) {
    private val logger = LoggerFactory.getLogger(MessageProperties::class.java)




    @Bean("R4")
    open fun fhirR4Context(): FhirContext {
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean
    @Primary
    fun customObjectMapper(): ObjectMapper {
        return ObjectMapper()
    }

    @Bean
    open fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    @Bean
    fun getCognitoService(messageProperties: MessageProperties, @Qualifier("R4") ctx : FhirContext, fhirServerProperties: FHIRServerProperties): CognitoAuthInterceptor? {
        return CognitoAuthInterceptor(messageProperties, fhirServerProperties, ctx)
    }

    @Bean
    fun getAWSclient(cognitoIdpInterceptor: CognitoAuthInterceptor?, mmessageProperties: MessageProperties, @Qualifier("R4") ctx : FhirContext): IGenericClient? {
        val client: IGenericClient = ctx.newRestfulGenericClient(mmessageProperties.getCdrFhirServer())
        client.registerInterceptor(cognitoIdpInterceptor)
        return client
    }
    @Bean
    fun corsFilter(): FilterRegistrationBean<*>? {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()
        config.allowCredentials = true
        config.addAllowedOrigin("*")
        config.addAllowedHeader("*")
        config.addAllowedMethod("*")
        source.registerCorsConfiguration("/**", config)
        val bean: FilterRegistrationBean<*> = FilterRegistrationBean<Filter>(CorsFilter())
        bean.order = 0
        return bean
    }
    @Bean
    fun getSQS(): AmazonSQS {
        var sqs: AmazonSQS = AmazonSQSClientBuilder.defaultClient();
        logger.info("AWS SQS Queue "+ messageProperties.getAwsQueueName() + " configuration");
        // Don't connect if not enabled
        if (!(messageProperties.getAWSQueueEnabled() == true)) return sqs;

        val create_request : CreateQueueRequest = CreateQueueRequest(messageProperties.getAwsQueueName())
            .addAttributesEntry("DelaySeconds", "60")
            .addAttributesEntry("MessageRetentionPeriod", "86400");


        try {
            sqs.createQueue(create_request);
        } catch (e : AmazonSQSException) {
            if (!e.getErrorCode().equals("QueueAlreadyExists")) {
                throw e;
            }
            logger.info("AWS SQS Queue "+ messageProperties.getAwsQueueName() + " already exists");
        }
        return sqs;
    }
    @Bean
    fun getCodeSystemsTerminology(ctx : FhirContext) : List<CodeSystem> {

        val classLoader = javaClass.classLoader
        var codeSystems = ArrayList<CodeSystem>()
        /*
        var ioFolder = classLoader.getResourceAsStream("TERM/")
        val files: List<String> = IOUtils.readLines(ioFolder, Charsets.UTF_8)
        for (file in files) {
            System.out.println(file)
            if (file.startsWith("codesystem")) {
                file)
                val text = IOUtils.toString(classLoader.getResourceAsStream("TERM/"+file),Charsets.UTF_8)
                val resource = ctx.newXmlParser().parseResource(text)
                if (resource is CodeSystem) codeSystems.add(resource)
            }
        }*/
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-audit_change_type.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-composition_category.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-compression_algorithms.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-event_math_function.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-extract_action_type.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-extract_content_type.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-extract_update_trigger_event_type.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-instruction_states.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-instruction_transitions.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-integrity_check_algorithms.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-normal_statuses.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-null_flavours.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-participation_function.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-participation_mode.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-property.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-setting.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-subject_relationship.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-term_mapping_purpose.xml"),Charsets.UTF_8)) as CodeSystem)
        codeSystems.add(ctx.newXmlParser().parseResource(IOUtils.toString(classLoader.getResourceAsStream("TERM/codesystem-version_lifecycle_state.xml"),Charsets.UTF_8)) as CodeSystem)
        return codeSystems
    }

}
