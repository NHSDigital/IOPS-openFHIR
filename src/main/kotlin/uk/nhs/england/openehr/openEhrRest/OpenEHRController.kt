package uk.nhs.england.openehr.openEhrRest

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import io.swagger.v3.oas.annotations.Hidden
import mu.KLogging
import org.apache.xmlbeans.XmlException
import org.hl7.fhir.r4.model.*
import org.openehr.schemas.v1.*

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.openehr.schemas.v1.impl.ARCHETYPESLOTImpl
import org.openehr.schemas.v1.impl.CARCHETYPEROOTImpl
import org.openehr.schemas.v1.impl.CCODEPHRASEImpl
import org.openehr.schemas.v1.impl.CCOMPLEXOBJECTImpl
import org.openehr.schemas.v1.impl.CDVORDINALImpl
import org.openehr.schemas.v1.impl.CMULTIPLEATTRIBUTEImpl
import org.openehr.schemas.v1.impl.CPRIMITIVEOBJECTImpl
import org.openehr.schemas.v1.impl.CSINGLEATTRIBUTEImpl
import uk.nhs.england.openehr.model.openEHRtoFHIR
import uk.nhs.england.openehr.util.FhirSystems.*
import kotlin.random.Random

@RestController
@Hidden
class OpenEHRController(
    @Qualifier("R4") private val fhirContext: FhirContext,
) {
    companion object : KLogging()

    @PostMapping("/openehr/\$convertOpenEHRArchetype", produces = ["application/json", "application/xml"])
    fun convertArchetype(
        @RequestBody archetypXML: String,

        ): String {

        val document: ArchetypeDocument
        document = try {
            ArchetypeDocument.Factory.parse(ByteArrayInputStream(archetypXML.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.archetype !== null) {
            val archetype = document.archetype
            val openEHRtoFHIR = openEHRtoFHIR(document.archetype)
            return fhirContext.newJsonParser().encodeResourceToString(openEHRtoFHIR.questionnaire)
        }
        return "Error"
    }

    @PostMapping("/openehr/\$convertOpenEHRTemplate", produces = ["application/json", "application/xml"])
    fun convertTemplate(
        @RequestBody templateXML: String,

        ): String {

        val document: TemplateDocument
        document = try {
            TemplateDocument.Factory.parse(ByteArrayInputStream(templateXML.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: XmlException) {
            throw UnprocessableEntityException(e.message)
        } catch (e: IOException) {
            throw UnprocessableEntityException(e.message)
        }

        if (document.template !== null) {
            val template = document.template
            val openEHRtoFHIR = openEHRtoFHIR(document.template)
            return fhirContext.newJsonParser().encodeResourceToString(openEHRtoFHIR.questionnaire)
        }
        return "Error"
    }


}





