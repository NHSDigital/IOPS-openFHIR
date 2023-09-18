package uk.nhs.england.openehr.transform

import com.nedap.archie.rm.archetyped.Archetyped
import com.nedap.archie.rm.archetyped.TemplateId
import com.nedap.archie.rm.composition.Composition
import com.nedap.archie.rm.composition.Section
import com.nedap.archie.rm.datatypes.CodePhrase
import com.nedap.archie.rm.datavalues.DvCodedText
import com.nedap.archie.rm.datavalues.DvText
import com.nedap.archie.rm.generic.PartyIdentified
import com.nedap.archie.rm.support.identification.ArchetypeID
import com.nedap.archie.rm.support.identification.ObjectVersionId
import com.nedap.archie.rm.support.identification.TerminologyId
import org.hl7.fhir.r4.model.*
import uk.nhs.england.openehr.util.FhirSystems



class FHIRQuestionnaireResponseToOpenEHRComposition(
    val fhirQuestionnaireResponseExtract: FHIRQuestionnaireResponseExtract
) {

    fun convert(
         questionnaireResponse: QuestionnaireResponse
    ) : Composition? {
        var composition: Composition? = null
        val questionnaire = fhirQuestionnaireResponseExtract.getQuestionnaire(questionnaireResponse)
        if (questionnaire !== null ) {
            composition = Composition()
            composition.archetypeNodeId = "openEHR-EHR-COMPOSITION.encounter.v1"
            composition.name = DvText(questionnaire.title)
            composition.uid = ObjectVersionId(questionnaireResponse.idPart)

            composition.archetypeDetails = Archetyped()
            if (questionnaire.url.contains(FhirSystems.OPENEHR_QUESTIONNAIRE_TEMPLATE)) {
                composition.archetypeDetails.templateId = TemplateId()
                composition.archetypeDetails.templateId!!.value = questionnaire.url.substringAfter(FhirSystems.OPENEHR_QUESTIONNAIRE_TEMPLATE)
            }
            if (questionnaire.derivedFrom.size>0 && questionnaire.derivedFrom[0].value.contains(
                    FhirSystems.OPENEHR_QUESTIONNAIRE_ARCHETYPE
                )) {
                composition.archetypeDetails.archetypeId = ArchetypeID()
                composition.archetypeDetails.archetypeId!!.value = questionnaire.derivedFrom[0].value.substringAfter(
                    FhirSystems.OPENEHR_QUESTIONNAIRE_ARCHETYPE
                )
            }
            composition.language = CodePhrase()
            composition.language.terminologyId = TerminologyId()
            composition.language.terminologyId.value = "ISO_639-1"
            composition.language.codeString = "en"
            composition.territory = CodePhrase()
            composition.territory.terminologyId = TerminologyId()
            composition.territory.terminologyId.value = "ISO_3166-1"
            composition.territory.codeString = "SI"

            // Category move from item extension into root\\
            for (qItem in questionnaire.item) {
                for (extension in qItem.extension) {
                    if (extension.url.equals(FhirSystems.OPENEHR_COMPOSITION_CATEGORY_EXT) ) {
                        // Probably needs to be in root resource
                        composition.category = DvCodedText()
                        if (extension.value is Coding) {
                            composition.category.value = (extension.value as Coding).display
                            composition.category.definingCode = CodePhrase()
                            composition.category.definingCode.terminologyId = TerminologyId()
                            composition.category.definingCode.terminologyId.value = "openehr"
                            composition.category.definingCode.codeString = (extension.value as Coding).code
                        }
                    }
                }
            }
            // Performer

            if (questionnaireResponse.hasAuthor()) {
                if (questionnaireResponse.author.hasDisplay()) {
                    // TODO both nulls - this seems to be a Reference
                    composition.composer = PartyIdentified(null,
                        questionnaireResponse.author.display, null)
                }
            }

            // Context

            // This appears to be a FHIR Encounter. Is optional so leave for now

            for (item in questionnaireResponse.item) {
                var qItem = fhirQuestionnaireResponseExtract.getItem(questionnaire,item.linkId)

                var content = Section()
                content.name = DvText(item.text)
                content.archetypeNodeId = getArchetype(item.linkId)
                composition.content.add(content)
                if (item.hasItem()) {
                    for (sitem in item.item) {
                        var contentS = com.nedap.archie.rm.composition.Observation()
                        contentS.name = DvText(sitem.text)
                        contentS.archetypeNodeId = getArchetype(sitem.linkId)
                        content.addItem(contentS)
                    }
                }
            }



        }
        return composition
    }

    private fun getArchetype(linkId: String?): String? {
        if (linkId == null)  return "ERROR"
        val archetypes = linkId.split("/")
        return archetypes[archetypes.size-1]
    }

}
