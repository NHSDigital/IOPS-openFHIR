package uk.nhs.england.openehr.interceptor

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import org.hl7.fhir.instance.model.api.IBaseConformance
import org.hl7.fhir.r4.model.*
import uk.nhs.england.openehr.configuration.FHIRServerProperties


@Interceptor
class CapabilityStatementInterceptor(
    fhirContext: FhirContext,
    private val fhirServerProperties: FHIRServerProperties
) {

    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    fun customize(theCapabilityStatement: IBaseConformance) {

        // Cast to the appropriate version
        val cs: CapabilityStatement = theCapabilityStatement as CapabilityStatement

        // Customize the CapabilityStatement as desired
        val apiextension = Extension();
        apiextension.url = "https://fhir.nhs.uk/StructureDefinition/Extension-NHSDigital-CapabilityStatement-Package"

        val packageExtension = Extension();
        packageExtension.url="openApi"
        packageExtension.extension.add(Extension().setUrl("documentation").setValue(UriType("https://simplifier.net/guide/NHSDigital/Home")))
        packageExtension.extension.add(Extension().setUrl("description").setValue(StringType("NHS England FHIR Implementation Guide")))
        apiextension.extension.add(packageExtension)
        cs.extension.add(apiextension)

        cs.name = fhirServerProperties.server.name
        cs.software.name = fhirServerProperties.server.name
        cs.software.version = fhirServerProperties.server.version
        cs.publisher = "NHS England"
        cs.implementation.url =  fhirServerProperties.server.baseUrl + "/FHIR/R4"
        cs.implementation.description = "NHS England FHIR Implementation Guide"
        if (fhirServerProperties.server.smart && cs.hasRest()) {
            var extension = Extension().setUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris")
            extension.addExtension(Extension().setUrl("authorize").setValue(UriType().setValue(fhirServerProperties.server.authorize)))
            extension.addExtension(Extension().setUrl("token").setValue(UriType().setValue(fhirServerProperties.server.token)))
            extension.addExtension(Extension().setUrl("introspect").setValue(UriType().setValue(fhirServerProperties.server.introspect)))
            cs.restFirstRep.security.extension.add(extension)
            cs.restFirstRep.security.cors = true
            cs.restFirstRep.security.service.add(
                CodeableConcept().addCoding(Coding().setCode("SMART-on-FHIR").setDisplay("SMART-on-FHIR").setSystem("http://hl7.org/fhir/restful-security-service")).setText("OAuth2 using SMART-on-FHIR profile (see http://docs.smarthealthit.org)")
            )
        }
    }

    fun getResourceComponent(type : String, cs : CapabilityStatement ) : CapabilityStatement.CapabilityStatementRestResourceComponent? {
        for (rest in cs.rest) {
            for (resource in rest.resource) {
                // println(type + " - " +resource.type)
                if (resource.type.equals(type))
                    return resource
            }
        }
        return null
    }


}
