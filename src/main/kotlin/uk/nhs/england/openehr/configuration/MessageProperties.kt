package uk.nhs.england.openehr.configuration

import ca.uhn.fhir.context.ConfigurationException
import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import java.util.*

@Configuration
class MessageProperties {
    private val log = LoggerFactory.getLogger(MessageProperties::class.java)


    val HAPI_PROPERTIES = "message.properties"


    val SERVER_FACILITY = "server.facility"
    val SERVER_APPLICATION = "server.application"

    val HL7_ROUTE_EXCEPTION = "hl7.route.exception"
    val HL7_ROUTE_FILE_IN = "hl7.route.fileIn"
    val HL7_ROUTE_FILE_OUT = "hl7.route.fileOut"
    val HL7_ROUTE_MLLP = "hl7.route.MLLP"

    val AWS_CLIENT_ID = "aws.clientId"
    val AWS_CLIENT_SECRET = "aws.clientSecret"
    val AWS_TOKEN_URL = "aws.tokenUrl"
    val AWS_CLIENT_USER = "aws.user"
    val AWS_CLIENT_PASS = "aws.pass"
    val AWS_API_KEY = "aws.apiKey"

    val AWS_QUEUE_NAME = "aws.queueName"
    val AWS_QUEUE_ENABLED = "aws.queueEnabled"
    val CDR_FHIR_SERVER = "cdr.fhirServer"
    val VALIDATION_FHIR_SERVER = "validation.fhirServer"

    private var properties: Properties? = null

    /*
     * Force the configuration to be reloaded
     */
    fun forceReload() {
        properties = null
        getProperties()
    }

    /**
     * This is mostly here for unit tests. Use the actual properties file
     * to set values
     */
    @VisibleForTesting
    fun setProperty(theKey: String?, theValue: String?) {
        getProperties()!!.setProperty(theKey, theValue)
    }

    fun getProperties(): Properties? {
        if (properties == null) {
            // Load the configurable properties file
            try {
                MessageProperties::class.java.classLoader.getResourceAsStream(HAPI_PROPERTIES).use { `in` ->
                    properties = Properties()
                    properties!!.load(`in`)
                }
            } catch (e: Exception) {
                throw ConfigurationException("Could not load HAPI properties", e)
            }
            val overrideProps = loadOverrideProperties()
            if (overrideProps != null) {
                properties!!.putAll(overrideProps)
            }
        }
        return properties
    }

    /**
     * If a configuration file path is explicitly specified via -Dfhir.properties=<path>, the properties there will
     * be used to override the entries in the default fhir.properties file (currently under WEB-INF/classes)
     * @return properties loaded from the explicitly specified configuraiton file if there is one, or null otherwise.
    </path> */
    private fun loadOverrideProperties(): Properties? {
        val confFile = System.getProperty(HAPI_PROPERTIES)
        return if (confFile != null) {
            try {
                val props = Properties()
                props.load(FileInputStream(confFile))
                props
            } catch (e: Exception) {
                throw ConfigurationException("Could not load HAPI properties file: $confFile", e)
            }
        } else null
    }

    private fun getProperty(propertyName: String): String? {
        return getProperty(propertyName, null)
    }

    private fun getProperty(propertyName: String, defaultValue: String?): String? {
        val properties: Properties? = getProperties()
        log.trace("Looking for property = {}", propertyName)
        if (System.getenv(propertyName) != null) {
            val value = System.getenv(propertyName)
            log.debug("System Environment property Found {} = {}", propertyName, value)
            return value
        }
        if (System.getProperty(propertyName) != null) {
            val value = System.getenv(propertyName)
            log.debug("System Property Found {} = {}", propertyName, value)
            return value
        }
        if (properties != null) {
            val value = properties.getProperty(propertyName)
            if (value != null && value.length > 0) {
                return value
            }
        }
        return defaultValue
    }

    private fun getPropertyBoolean(propertyName: String, defaultValue: Boolean): Boolean {
        val value: String? = getProperty(propertyName)
        return if (value == null || value.length == 0) {
            defaultValue
        } else java.lang.Boolean.parseBoolean(value)
    }

    private fun getPropertyInteger(propertyName: String, defaultValue: Int): Int? {
        val value: String? = getProperty(propertyName)
        return if (value == null || value.length == 0) {
            defaultValue
        } else value.toInt()
    }

    private fun <T : Enum<*>?> getPropertyEnum(thePropertyName: String, theEnumType: Class<T>, theDefaultValue: T): T {
        val value = getProperty(thePropertyName, theDefaultValue!!.name)
        return java.lang.Enum.valueOf(theEnumType, value) as T
    }


    fun getEmailEnabled(): Boolean? {
        return getPropertyBoolean("email.enabled", false)
    }

    fun getEmailHost(): String? {
        return getProperty("email.host")
    }

    fun getEmailPort(): Int? {
        return getPropertyInteger("email.port", 0)
    }

    fun getEmailUsername(): String? {
        return getProperty("email.username")
    }

    fun getEmailPassword(): String? {
        return getProperty("email.password")
    }


    private fun loadProperties(): Properties? {
        // Load the configurable properties file
        var properties: Properties
        try {
            MessageProperties::class.java.classLoader.getResourceAsStream(HAPI_PROPERTIES).use { `in` ->
                properties = Properties()
                properties.load(`in`)
            }
        } catch (e: Exception) {
            throw ConfigurationException("Could not load HAPI properties", e)
        }
        val overrideProps = loadOverrideProperties()
        if (overrideProps != null) {
            properties.putAll(overrideProps)
        }
        return properties
    }


    fun getServerFacility(): String? {
        return getProperty(SERVER_FACILITY, "ODS_CODE")
    }

    fun getServerApplication(): String? {
        return getProperty(SERVER_APPLICATION, "EPR")
    }

    fun getHl7RouteException(): String? {
        return getProperty(HL7_ROUTE_EXCEPTION, "file:///HL7v2/Error")
    }

    fun getHl7RouteFileIn(): String? {
        return getProperty(HL7_ROUTE_FILE_IN, "")
    }

    fun getHl7RouteFileOut(): String? {
        return getProperty(HL7_ROUTE_FILE_OUT, "file:///HL7v2/Out")
    }

    fun getHl7RouteMllp(): String? {
        return getProperty(HL7_ROUTE_MLLP)
    }

    fun getAwsClientId(): String? {
        return getProperty(AWS_CLIENT_ID)
    }

    fun getAwsClientSecret(): String? {
        return getProperty(AWS_CLIENT_SECRET)
    }

    fun getAwsTokenUrl(): String? {
        return getProperty(AWS_TOKEN_URL)
    }

    fun getAwsClientUser(): String? {
        return getProperty(AWS_CLIENT_USER)
    }

    fun getAwsClientPass(): String? {
        return getProperty(AWS_CLIENT_PASS)
    }

    fun getAwsQueueName(): String? {
        return getProperty(AWS_QUEUE_NAME)
    }

    fun getAWSQueueEnabled(): Boolean {
        return getPropertyBoolean(AWS_QUEUE_ENABLED, false)
    }


    fun getAwsApiKey(): String? {
        return getProperty(AWS_API_KEY)
    }

    fun getCdrFhirServer(): String? {
        return getProperty(CDR_FHIR_SERVER)
    }

    fun getValidationFhirServer(): String? {
        return getProperty(VALIDATION_FHIR_SERVER)
    }
}
