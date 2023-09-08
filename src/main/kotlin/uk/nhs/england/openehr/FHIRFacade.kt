package uk.nhs.england.openehr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan
import uk.nhs.england.openehr.configuration.FHIRServerProperties


@SpringBootApplication
@ServletComponentScan
@EnableConfigurationProperties(FHIRServerProperties::class)
open class FHIRFacade

fun main(args: Array<String>) {
    runApplication<uk.nhs.england.openehr.FHIRFacade>(*args)
}
