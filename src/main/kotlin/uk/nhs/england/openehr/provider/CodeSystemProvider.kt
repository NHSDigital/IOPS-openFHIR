package uk.nhs.england.openehr.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
class CodeSystemProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                          private val codeSystems: List<CodeSystem>
) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<CodeSystem> {
        return CodeSystem::class.java
    }


    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): CodeSystem? {
        for (codesystem in codeSystems) {
            val id = getId(codesystem)
            if (id != null && id.equals(internalId.idPart)) return codesystem
        }
        return null
    }

    @Search
    fun search(@OptionalParam(name = CodeSystem.SP_URL) url: TokenParam?): List<CodeSystem> {
        val list = mutableListOf<CodeSystem>()

        for (codesystem in codeSystems) {

            if (url == null) {
                codesystem.id = getId(codesystem)
                list.add(codesystem)
            } else {
                if (codesystem.url.equals(url.value)) {
                    codesystem.id = getId(codesystem)
                    list.add(codesystem)
                }
            }
        }
        return list
    }
    fun getId(codeSystem: CodeSystem) : String? {
        val url = codeSystem.url.split("/")
        if (url.size>0) return url[url.size-1]
        return null
    }
}
