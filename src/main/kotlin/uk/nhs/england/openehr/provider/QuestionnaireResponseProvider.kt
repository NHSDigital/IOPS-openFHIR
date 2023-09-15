package uk.nhs.england.openehr.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.nedap.archie.rm.composition.Composition
import com.nedap.archie.rm.composition.ContentItem
import com.nedap.archie.rm.composition.Entry
import com.nedap.archie.rm.composition.Section
import com.nedap.archie.rm.datavalues.DvText
import com.nedap.archie.rm.support.identification.ObjectVersionId
import com.nedap.archie.rm.support.identification.UIDBasedId
import org.apache.xmlbeans.XmlOptions
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl
import org.ehrbase.serialisation.jsonencoding.CanonicalJson
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.openehr.schemas.v1.CompositionDocument
import org.openehr.schemas.v1.DVCODEDTEXT
import org.openehr.schemas.v1.impl.DVCODEDTEXTImpl
import org.springframework.stereotype.Component
import uk.nhs.england.openehr.awsProvider.AWSQuestionnaire
import uk.nhs.england.openehr.util.FhirSystems
import java.util.*


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class QuestionnaireResponseProvider(

    var awsQuestionnaire: AWSQuestionnaire
) : IResourceProvider {
    override fun getResourceType(): Class<QuestionnaireResponse> {
        return QuestionnaireResponse::class.java
    }

    @Operation(name = "\$convertOpenEHRComposition", idempotent = true, manualResponse = true)
    fun convertOpenEHRComposition(
        response: HttpServletResponse,
        @ResourceParam questionnaireResponse: QuestionnaireResponse
    ) {

        val questionnaire = getQuestionnaire(questionnaireResponse)
        if (questionnaire !== null ) {
            val composition = Composition()
            composition.archetypeNodeId = "openEHR-EHR-COMPOSITION.encounter.v1"
            composition.name = DvText(questionnaire.title)
            composition.uid = ObjectVersionId(questionnaireResponse.idPart)

            for (item in questionnaireResponse.item) {
                var content = Section()
                content.name = DvText(item.text)
                content.archetypeNodeId = item.linkId
                composition.content.add(content)
                if (item.hasItem()) {
                    for (sitem in item.item) {
                        var contentS = com.nedap.archie.rm.composition.Observation()
                        contentS.name = DvText(sitem.text)
                        contentS.archetypeNodeId = sitem.linkId
                        content.addItem(contentS)
                    }
                }
            }


            val json = CanonicalJson().marshal(composition)
            response.setHeader("Content-Type","application/json")
            response.writer.write(json)
        } else {
            throw UnprocessableEntityException("Questionnaire not found")
        }
    }

    fun getQuestionnaire(questionnaireResponse: QuestionnaireResponse) : Questionnaire? {
        var questionnaire : Questionnaire? = null
        var questionnaireList = awsQuestionnaire.search(UriParam().setValue(questionnaireResponse?.questionnaire))
        if (questionnaireList == null || questionnaireList.size==0) {
            var result = awsQuestionnaire.read(IdType().setValue(questionnaireResponse.questionnaire))
            if (result !== null && result.resource !== null) {
                questionnaire = result.resource as Questionnaire
            }
        } else {
            questionnaire = questionnaireList[0]
        }
        return  questionnaire
    }

    @Operation(name = "\$extract", idempotent = true, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/QuestionnaireResponse-extract")
    fun expand(@ResourceParam questionnaireResponse: QuestionnaireResponse
    ): Bundle {
        var bundle: Bundle = Bundle();
        bundle.type = Bundle.BundleType.TRANSACTION;
        if (!questionnaireResponse.hasQuestionnaire()) throw UnprocessableEntityException("Questionnaire must be supplied");
        val questionnaire = getQuestionnaire(questionnaireResponse)
        if (questionnaire !== null ) {
            processItem(bundle, questionnaire, questionnaireResponse, questionnaireResponse.item, null)
        } else {
            throw UnprocessableEntityException("Questionnaire not found")
        }
        return bundle;
    }

    private fun processItem(bundle: Bundle, questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse, items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>, parentObservation : Resource?) {
        var mainResource = parentObservation
        var isOpenEHR = false
        for(item in items) {
            var questionItem = getItem(questionnaire, item.linkId)
            var generateObservation = false;
            if (questionItem.hasExtension()) {
                var tempIsOpenEHR = false
                for (extension in questionItem.extension) {
                    if (extension.url.equals("http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-observationExtract")
                        && extension.value is BooleanType) {
                        generateObservation = (extension.value as BooleanType).value
                    }
                    if (extension.url.equals(FhirSystems.OPENEHR_DATATYPE_EXT)) {
                        tempIsOpenEHR = true
                    }
                }
                if (generateObservation) isOpenEHR = tempIsOpenEHR
            }
            if (generateObservation && questionItem.hasCode() && item.answerFirstRep != null) {

                for (answer in item.answer) {

                    var observation = Observation()
                    var entry = BundleEntryComponent()
                    var uuid = UUID.randomUUID();
                    entry.fullUrl = "urn:uuid:" + uuid.toString()
                    entry.resource = observation
                    entry.request.url = "Observation"
                    entry.request.method = Bundle.HTTPVerb.POST
                    bundle.entry.add(entry)

                    if (isOpenEHR) mainResource = observation

                    observation.status = Observation.ObservationStatus.FINAL;
                    observation.derivedFrom.add(Reference().setReference(questionnaireResponse.id))
                    if (questionnaireResponse.hasIdentifier()) {
                        var identifier = Identifier()
                        identifier.system = questionnaireResponse.identifier.system
                        identifier.value = questionnaireResponse.identifier.value + item.linkId
                        observation.addIdentifier(identifier)
                    }
                    if (questionnaireResponse.hasAuthor()) {
                        observation.addPerformer(questionnaireResponse.author)
                    }
                    observation.code = CodeableConcept()
                    observation.code.coding = questionItem.code
                    observation.setSubject(questionnaireResponse.subject)
                    if (questionnaireResponse.hasAuthored()) {
                        observation.setEffective(DateTimeType().setValue(questionnaireResponse.authored ))
                        observation.setIssued(questionnaireResponse.authored )
                    }

                    // answers

                    if (answer.hasValueDateType()) {
                        observation.setEffective(DateTimeType().setValue(answer.valueDateType.value))
                        observation.setValue(DateTimeType().setValue(answer.valueDateType.value))
                    } else if (answer.hasValueDateTimeType()) {
                        observation.setEffective(answer.valueDateTimeType)
                        observation.setValue(answer.valueDateTimeType)
                    } else if (answer.hasValueCoding()) {
                        observation.setValue(CodeableConcept().addCoding(answer.valueCoding))
                    } else if (answer.hasValueDecimalType()) {
                        observation.setValue(Quantity().setValue(answer.valueDecimalType.value))
                    } else {
                        // fall through - may have some issues here
                        if (answer.hasValue()) {
                            observation.setValue(answer.value)
                        }
                    }
                    //
                    if (item.hasItem()) {
                        processItem(bundle, questionnaire, questionnaireResponse, item.item, observation)
                    }

                }
            } else {
                // Need to capture the mapping here
                if (questionItem.hasDefinition()) {
                    when (questionItem.definition) {
                        "Condition" -> {
                            var condition = Condition()
                            var entry = BundleEntryComponent()
                            var uuid = UUID.randomUUID();
                            entry.fullUrl = "urn:uuid:" + uuid.toString()
                            entry.resource = condition
                            entry.request.url = "Condition"
                            entry.request.method = Bundle.HTTPVerb.POST
                            bundle.entry.add(entry)

                            if (questionnaireResponse.hasIdentifier()) {
                                var identifier = Identifier()
                                identifier.system = questionnaireResponse.identifier.system
                                identifier.value = questionnaireResponse.identifier.value + item.linkId
                                condition.addIdentifier(identifier)
                            }
                            if (questionnaireResponse.hasAuthor()) {
                                condition.recorder = questionnaireResponse.author
                            }

                            condition.setSubject(questionnaireResponse.subject)
                            if (questionnaireResponse.hasAuthored()) {
                                condition.setRecordedDate(questionnaireResponse.authored )
                            }

                            mainResource = condition
                        }
                    }
                }
                if (mainResource !== null)
                {
                    if (mainResource is Observation && questionItem.hasDefinition()) {
                        when (questionItem.definition) {
                            "Observation.note" -> {
                                if (item.hasAnswer() && item.answer.size > 0 && item.answerFirstRep.hasValueStringType()) mainResource.addNote(
                                    Annotation().setText(item.answerFirstRep.valueStringType.value)
                                )
                            }

                            "Observation.interpretation" -> {
                                if (item.hasAnswer() && item.answer.size > 0 && item.answerFirstRep.hasValueStringType()) {
                                    mainResource.addInterpretation(CodeableConcept().setText(item.answerFirstRep.valueStringType.value))
                                }
                            }

                            else -> {
                                System.out.println(questionItem.definition)
                            }

                        }
                    }

                    if (mainResource is Condition && questionItem.hasDefinition()) {
                        when (questionItem.definition) {
                            "Condition.code" -> {
                                if (item.hasAnswer()) {
                                    for (answer in item.answer) {
                                        if (answer.hasValueCoding()) mainResource.code.coding.add(answer.valueCoding)
                                    }
                                }
                            }

                            "Condition.status" -> {
                                if (item.hasAnswer()) {
                                    for (answer in item.answer) {
                                        if (answer.hasValueCoding()) {

                                              (mainResource as Condition).clinicalStatus.coding.add(answer.valueCoding)
                                        }
                                    }
                                }
                            }

                            else -> {
                                System.out.println(questionItem.definition)
                            }

                        }
                    }
                }
                if (item.hasItem()) {
                    processItem(bundle, questionnaire, questionnaireResponse, item.item, mainResource)
                }
            }

        }
    }

    private fun getItem(questionnaire: Questionnaire, linkId: String): Questionnaire.QuestionnaireItemComponent {
        var result = getSubItems(questionnaire.item, linkId)
        if (result != null) return result
        throw UnprocessableEntityException("linkId not found " + linkId)
    }

    private fun getSubItems(items : List<Questionnaire.QuestionnaireItemComponent>, linkId: String): Questionnaire.QuestionnaireItemComponent? {
        for (item in items) {
            if (linkId.equals(item.linkId)) return item;
            if (item.hasItem()) {
                var result = getSubItems(item.item, linkId)
                if (result != null) return result
            }
        }
        return null
    }


}
