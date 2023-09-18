package uk.nhs.england.openehr.transform

import com.nedap.archie.rm.archetyped.Archetyped
import com.nedap.archie.rm.archetyped.TemplateId
import com.nedap.archie.rm.composition.Composition
import com.nedap.archie.rm.composition.ContentItem
import com.nedap.archie.rm.composition.Entry
import com.nedap.archie.rm.composition.Observation
import com.nedap.archie.rm.composition.Section
import com.nedap.archie.rm.datastructures.Cluster
import com.nedap.archie.rm.datastructures.History
import com.nedap.archie.rm.datastructures.ItemStructure
import com.nedap.archie.rm.datastructures.ItemTree
import com.nedap.archie.rm.datatypes.CodePhrase
import com.nedap.archie.rm.datavalues.DvCodedText
import com.nedap.archie.rm.datavalues.DvText
import com.nedap.archie.rm.generic.PartyIdentified
import com.nedap.archie.rm.support.identification.ArchetypeID
import com.nedap.archie.rm.support.identification.ObjectVersionId
import com.nedap.archie.rm.support.identification.TerminologyId
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent
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
            composition.language = getLanguage()
            composition.territory = getTerritory()

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
                var content = processContentItem(composition,questionnaire,item)
                composition.addContent(content)
            }



        }
        return composition
    }

    private fun getTerritory(): CodePhrase? {
        val territory = CodePhrase()
        territory.terminologyId = TerminologyId()
        territory.terminologyId.value = "ISO_3166-1"
        territory.codeString = "SI"
        return territory
    }

    private fun getLanguage(): CodePhrase? {
        val language = CodePhrase()
        language.terminologyId = TerminologyId()
        language.terminologyId.value = "ISO_639-1"
        language.codeString = "en"
        return language
    }

    private fun getEncoding(): CodePhrase? {
        val language = CodePhrase()
        language.terminologyId = TerminologyId()
        language.terminologyId.value = "IANA_character-sets"
        language.codeString = "UTF-8"
        return language
    }

    fun processContentItem(composition: Composition, questionnaire: Questionnaire, item : QuestionnaireResponseItemComponent) : ContentItem? {
        var qItem = fhirQuestionnaireResponseExtract.getItem(questionnaire,item.linkId)
        val archetypeId = getArchetypeId(item.linkId)
        val archetypeType = getArchetypeType(item.linkId)
        var contentItem : ContentItem? = null
        when (archetypeType) {
            "OBSERVATION" -> {
                contentItem = Observation()

            }
            "CLUSTER" -> {
                //  contentItem = Cluster
            }
            else -> {
                contentItem = Section()
            }
        }
        if (contentItem != null && contentItem is Entry) {
            contentItem.language = getLanguage()
            contentItem.encoding = getEncoding()
            contentItem.archetypeNodeId = archetypeId
            contentItem.name = DvText(item.text)
        }

        if (contentItem != null && item.hasItem()) {
            for (sitem in item.item) {
               val subContentItem =  processData(contentItem,questionnaire,sitem)
                if (contentItem is Observation) {
                 //   (contentItem as Observation).a
                }
            }
        }
        return contentItem
    }

    private fun processData(contentItem: ContentItem, questionnaire: Questionnaire, item: QuestionnaireResponse.QuestionnaireResponseItemComponent): Any? {
        System.out.println(contentItem.name)
        val archetypeId = getArchetypeId(item.linkId)
        val archetypeType = getArchetypeType(item.linkId)
        when (archetypeType) {
            "ITEM_TREE" -> {
               System.out.println(archetypeType)
                val history = History<ItemStructure>()
                history.name = DvText(item.text)
                history.archetypeNodeId = archetypeId
                if (contentItem is Observation) contentItem.data = history
            }
            "CLUSTER" -> {
                //  contentItem = Cluster
                val cluster = Cluster()
            }
            else -> {
               // contentItem = Section()
            }
        }
        return null
    }

    private fun getArchetypeId(linkId: String?): String? {
        if (linkId == null)  return "ERROR"
        val archetypes = linkId.split("/")
        return archetypes[archetypes.size-1].substringBefore("[")
    }
    private fun getArchetypeType(linkId: String?): String? {
        if (linkId == null)  return "ERROR"
        val archetypes = linkId.split("/")
        return archetypes[archetypes.size-1].substringAfter("[").substringBefore("]")
    }


}
