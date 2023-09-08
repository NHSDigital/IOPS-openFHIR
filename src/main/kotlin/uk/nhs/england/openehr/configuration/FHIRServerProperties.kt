package uk.nhs.england.openehr.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "fhir")
data class FHIRServerProperties(
    var server: Server
) {
    data class Server(
        var baseUrl: String,
        var name: String,
        var version: String,
        var authorize: String,
        var token: String,
        var introspect: String,
        var smart: Boolean
    )
}
