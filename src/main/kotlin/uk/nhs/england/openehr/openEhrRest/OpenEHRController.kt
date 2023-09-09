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
        var questionnaire = Questionnaire()
        questionnaire.status= Enumerations.PublicationStatus.DRAFT

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
            if (archetype.archetypeId !== null) {
                questionnaire.url = "https://example.fhir.openehr.org/Questionnaire/" + archetype.archetypeId.value
                questionnaire.url = questionnaire.url.replace(" ", "")
                questionnaire.title = archetype.archetypeId.value
            }
            if (archetype.uid !== null) {
                questionnaire.identifier.add(Identifier().setValue(archetype.uid.value))
            }
            if (archetype.description !== null) {
                if (archetype.description.detailsArray !== null) {
                    questionnaire.purpose = archetype.description.detailsArray[0].purpose
                }
                if (archetype.description.originalAuthorArray !== null && archetype.description.originalAuthorArray.size > 0) {
                    for (author in archetype.description.originalAuthorArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.stringValue))
                    }
                }
                if (archetype.description.otherContributorsArray !== null && archetype.description.otherContributorsArray.size > 0) {
                    for (author in archetype.description.otherContributorsArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.toString()))
                    }
                }
            }
            if (archetype.definition !== null && archetype.definition.attributesArray !== null) {
                for (attribute in archetype.definition.attributesArray) {
                    processAttribute( null, questionnaire.item, null, attribute, archetype)
               }
            }
        }
        return fhirContext.newJsonParser().encodeResourceToString(questionnaire)
    }

    @PostMapping("/openehr/\$convertOpenEHRTemplate", produces = ["application/json", "application/xml"])
    fun convertTemplate(
        @RequestBody templateXML: String,

        ): String {
        val questionnaire = Questionnaire()
        questionnaire.status= Enumerations.PublicationStatus.DRAFT

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
            if (template.templateId !== null) {
                questionnaire.url = "https://example.fhir.openehr.org/Questionnaire/" + template.templateId.value
                questionnaire.url = questionnaire.url.replace(" ", "")
                questionnaire.title = template.templateId.value
            }
            if (template.uid !== null) {
                questionnaire.identifier.add(Identifier().setValue(template.uid.value))
            }
            if (template.description !== null) {
                if (template.description.detailsArray !== null) {
                    questionnaire.purpose = template.description.detailsArray[0].purpose
                }
                if (template.description.originalAuthorArray !== null && template.description.originalAuthorArray.size > 0) {
                    for (author in template.description.originalAuthorArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.stringValue))
                    }
                }
                if (template.description.otherContributorsArray !== null && template.description.otherContributorsArray.size > 0) {
                    for (author in template.description.otherContributorsArray) {
                        questionnaire.contact.add(ContactDetail().setName(author.toString()))
                    }
                }
            }
            if (template.definition !== null && template.definition.attributesArray !== null) {
                for (attribute in template.definition.attributesArray) {
                    if (attribute.rmAttributeName !== null && attribute.rmAttributeName.equals("content")) {
                        processAttribute(null, questionnaire.item, null, attribute, null)
                    }
                    //            processAttribute(questionnaire.item,attribute)
                }
            }
        }
        return fhirContext.newJsonParser().encodeResourceToString(questionnaire)
    }

    private fun processComplexObject(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl?,
        attribute: CCOMPLEXOBJECTImpl,
        ontology: ARCHETYPE?
    ) {
        var item = citem
        if (attribute.nodeId !== null) {
            // This adds in a new child
            if (attribute.rmTypeName !== null) {
                if (
                        attribute.rmTypeName.equals("ELEMENT")
                    //    || attribute.rmTypeName.equals("ITEM_TREE")
                    ) {
                var itemId = attribute.nodeId + "-" + Random.nextInt(0, 9999).toString()
                if (citem !== null) itemId = citem.linkId + "/" + itemId

                item = Questionnaire.QuestionnaireItemComponent().setLinkId( itemId)
                var name = "CCOMPLEXOBJECT"
                if (attribute.rmTypeName !== null) name = name + "/" + attribute.rmTypeName
                item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue(name)))

                item.code = getCoding(attribute.nodeId, carchetype, ontology)
                    var addSDC = false
                for(concept in item.code) {
                    if (concept.system !== null && (
                            concept.system.equals(SNOMED_CT) || concept.system.equals(LOINC)
                            )) addSDC = true
                }
                    if (addSDC) {
                        item.extension.add(Extension()
                            .setUrl(SDC_PERIOD)
                            .setValue(Duration()
                                .setValue(2)
                                .setCode("wk")
                                .setUnit("weeks")
                                .setSystem(UNITS_OF_MEASURE))
                        )
                        item.extension.add(Extension()
                            .setUrl(SDC_EXTRACT)
                            .setValue(BooleanType().setValue(true))
                        )
                    }
                item.text = getDisplay(attribute.nodeId, carchetype, ontology)
                item.type = Questionnaire.QuestionnaireItemType.STRING
                items.add(item)
            } else {
              System.out.println(attribute.rmTypeName)
                }
            }
        }

        if (attribute.attributesArray !== null) {
            if (item != null) {
                if (item.type != null) {
                    //       System.out.println("ComplexAttribute " + item.type)
                } else {
                    item.type = Questionnaire.QuestionnaireItemType.GROUP
                }
                for (attr in attribute.attributesArray) {
                    processAttribute(item, item.item, carchetype, attr, ontology)
                }
            } else {
                for (attr in attribute.attributesArray) {
                    processAttribute(item, items, carchetype, attr, ontology)
                }
            }
        }
        if (item != null) {
            if (attribute.rmTypeName.equals("ITEM_TREE")) {
                item.type = Questionnaire.QuestionnaireItemType.GROUP
            }
            if (attribute.rmTypeName.equals("DV_TEXT")) {
                // TODO add range in here ?
                if (item.type != null)  {
                    when(item.type) {
                        Questionnaire.QuestionnaireItemType.GROUP -> {
                            item.type = Questionnaire.QuestionnaireItemType.TEXT
                        }
                        Questionnaire.QuestionnaireItemType.CHOICE -> {
                            item.type = Questionnaire.QuestionnaireItemType.OPENCHOICE
                        }
                        else -> {
                       //     System.out.println("Not changed DV_TEXT  - "+ item.type)
                        }
                    }
                }
            }
            if (attribute.rmTypeName.equals("DV_COUNT")) {
                item.type = Questionnaire.QuestionnaireItemType.INTEGER
            }
            if (attribute.rmTypeName.equals("DV_CODED_TEXT")) {
                item.type = Questionnaire.QuestionnaireItemType.OPENCHOICE
            }
        }
    }

    private fun processArchetypeObject(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl,
        ontology: ARCHETYPE?
    ) {
        var item = citem
        val archetype = carchetype


        if (archetype.nodeId !== null) {
            // This adds in a new child
            var itemId = archetype.nodeId + "-" + Random.nextInt(0, 9999).toString()
            if (citem !== null) itemId = citem.linkId + "/" + itemId
            item = Questionnaire.QuestionnaireItemComponent().setLinkId( itemId)

            item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue("CARCHETYPEROOT")))

            item.code = getCoding(archetype.nodeId, archetype, ontology)
            item.text = getDisplay(archetype.nodeId, archetype, ontology)
            item.type = Questionnaire.QuestionnaireItemType.STRING
            items.add(item)
        }

        if (item != null) {

            if (archetype.archetypeId !== null) {
                item.extension.add(
                    Extension().setUrl(OPEN_EHR_ARCHETYPE).setValue(StringType().setValue(archetype.archetypeId.value))
                )
            }
            if (archetype.attributesArray !== null) {
                item.type = Questionnaire.QuestionnaireItemType.GROUP
                for (attribute in archetype.attributesArray) {
                    processAttribute(item, item.item, archetype, attribute, ontology)
                }
            }
        }
    }

    private fun processSingleObject(
        item: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl?,
        single: CSINGLEATTRIBUTEImpl,
        ontology: ARCHETYPE?
    ) {
     //   System.out.println(single.stringValue)
        if (item != null && single.childrenArray !== null) {
            for (attribute in single.childrenArray) {
               // processAttribute(item, item.item, attribute)
            }
        }
    }


    private fun processOrdinal(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>,
                               carchetype: CARCHETYPEROOTImpl?, ordinal: CDVORDINALImpl,
                               ontology: ARCHETYPE?) {
        if (item != null) {
            item.type = Questionnaire.QuestionnaireItemType.CHOICE
            if (ordinal.listArray != null) {
                for (ord in ordinal.listArray) {

                    var code = Coding()
                    if (ord.symbol != null
                        && ord.symbol.definingCode !== null
                        && ord.symbol.definingCode.codeString !== null) {
                        code.code = ord.symbol.definingCode.codeString
                        if (ontology != null) {
                            code.system = OPEN_EHR_CODESYSTEM + "/" + ontology.archetypeId.value
                        }
                        code.display = getDisplay(ord.symbol.definingCode.codeString, carchetype,ontology)
                    }
                    // TODO add value as score
                    var answerOption = Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                        code
                    )
                    answerOption.extension.add(
                        Extension().setUrl(SDC_ORDINAL_VALUE).setValue(DecimalType().setValue(ord.value.toBigDecimal()))
                    )
                    item.addAnswerOption(answerOption)
                }
            }
           // System.out.println(ordinal.stringValue)
        }
    }

    private fun processPrimative(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>,
                                 carchetype: CARCHETYPEROOTImpl?,primative: CPRIMITIVEOBJECTImpl,
                                 ontology: ARCHETYPE?) {
        if (item != null && primative.item != null && primative.rmTypeName != null) {
           when (primative.rmTypeName) {
               "INTEGER" -> item.type = Questionnaire.QuestionnaireItemType.INTEGER
               "REAL" -> item.type = Questionnaire.QuestionnaireItemType.DECIMAL
               "BOOLEAN" -> item.type = Questionnaire.QuestionnaireItemType.BOOLEAN
               "STRING" -> item.type = Questionnaire.QuestionnaireItemType.STRING
               else -> { // Note the block
                   throw UnprocessableEntityException("Unsupported type " + primative.rmTypeName)
               }
           }
        }
    }

    private fun processCodePhrase(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, carchetype: CARCHETYPEROOTImpl?,
                                  code: CCODEPHRASEImpl,
                                  ontology: ARCHETYPE?) {
        if (item !== null) {
            if (code.codeListArray !== null) {
                item.type= Questionnaire.QuestionnaireItemType.CHOICE
                    for (concept in code.codeListArray) {
                        if (code.terminologyId.value.equals("openehr")) {
                            item.answerValueSet = OPEN_EHR_VALUESET + "/" + concept
                        } else {
                            var coding = getCoding(concept, carchetype, ontology)
                            var code = Coding().setCode(concept).setDisplay(getDisplay(concept, carchetype, ontology))
                            if (coding.size > 0) {
                                code.setSystem(coding[0].system)
                                code.setCode(coding[0].code)
                            } else {
                                System.out.println(concept)
                            }
                            item.answerOption.add(
                                Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                                    code
                                )
                            )
                        }
                        // removed setSystem(OPEN_EHR_CODESYSTEM). as this didn't make sense
                    }
                }
            }
    }

    private fun getDisplay(code: String, archetype: CARCHETYPEROOTImpl?,
                           ontology: ARCHETYPE?) : String {
        var display =""
        if (ontology != null && ontology.ontology != null && ontology.ontology.termDefinitionsArray !== null) {
            for (terms in ontology.ontology.termDefinitionsArray) {
                for (items in terms.itemsArray) {
                    if (items.code.equals(code)) {
                        for (item in items.itemsArray) {
                            if (item.id.equals("text")) return item.stringValue
                            if (item.id.equals("description")) display = item.stringValue
                        }
                    }
                }
            }
        }
        if (archetype != null && archetype.termDefinitionsArray !== null) {
            for (term in archetype.termDefinitionsArray) {
                if (code.equals(term.code)) {
                    if (term.itemsArray !== null) {
                        for (item in term.itemsArray) {
                            if (item.id.equals("text")) return item.stringValue
                            if (item.id.equals("description")) display = item.stringValue
                        }
                    }
                }
            }
        }
        return display
    }

    private fun getCoding(nodeId: String , archetype: CARCHETYPEROOTImpl?,
                          ontology: ARCHETYPE?) : List<Coding> {
        val code = ArrayList<Coding>()
        if (ontology != null && ontology.ontology != null) {
            if (ontology.ontology.termBindingsArray != null) {
                for (termBinding in ontology.ontology.termBindingsArray) {
                    if (termBinding.terminology.equals("LOINC")) {
                        if (termBinding.itemsArray != null) {
                            for (codes in termBinding.itemsArray) {
                                if (codes.code.equals(nodeId)) code.add(
                                    Coding().setSystem(LOINC).setCode(codes.value.codeString)
                                )
                            }
                        }
                    }
                    if (termBinding.terminology.equals("SNOMED-CT")) {
                        if (termBinding.itemsArray != null) {
                            for (codes in termBinding.itemsArray) {
                                if (codes.code.equals(nodeId)) code.add(
                                    Coding().setSystem(SNOMED_CT).setCode(codes.value.codeString)
                                )
                            }
                        }
                    }
                }
            }
            if (ontology.ontology.termDefinitionsArray != null) {
                for (termBinding in ontology.ontology.termDefinitionsArray) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) for (item in codes.itemsArray) {
                                if (item.id.equals("text")) {
                                    code.add(
                                        Coding().setCode(nodeId).setDisplay(item.stringValue).setSystem(
                                            OPEN_EHR_CODESYSTEM + "/" + ontology.archetypeId.value))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (archetype != null && archetype.termBindingsArray != null) {
            for (termBinding in archetype.termBindingsArray) {
                if (termBinding.terminology.equals("LOINC")) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) code.add(Coding().setSystem(LOINC).setCode(codes.value.codeString))
                        }
                    }
                }
                if (termBinding.terminology.equals("SNOMED-CT")) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) code.add(Coding().setSystem(SNOMED_CT).setCode(codes.value.codeString))
                        }
                    }
                }
            }
        }
        return code
    }

    private fun processArchetypeSlot(citem: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>,
                                     carchetype: CARCHETYPEROOTImpl?,
                                     archetype:  ARCHETYPESLOTImpl) {
        var item = citem

/* I think this is needed for internal definitions
        if (archetype.nodeId !== null) {
            // This adds in a new child
            var itemId = archetype.nodeId + "-" + Random.nextInt(0, 9999).toString()
            if (citem !== null) itemId = citem.linkId + "/" + itemId
            item = Questionnaire.QuestionnaireItemComponent().setLinkId(itemId)
            item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue("CARCHETYPEROOT")))

            item.code = getCoding(archetype.nodeId, carchetype)
            item.text = getDisplay(archetype.nodeId, carchetype)

            item.type = Questionnaire.QuestionnaireItemType.DISPLAY
           // if (!archetype.rmTypeName.equals("CLUSTER")) System.out.println("Slot - " + archetype.rmTypeName)
            items.add(item)
        }
*/

    }

    private fun processQuantity(item: Questionnaire.QuestionnaireItemComponent?,
                                items: MutableList<Questionnaire.QuestionnaireItemComponent>,
                                carchetype: CARCHETYPEROOTImpl?,
                                quantity: CDVQUANTITY) {
        if (item !== null) {
            item.type = Questionnaire.QuestionnaireItemType.QUANTITY
            if (quantity.listArray !== null) {
                for (list in quantity.listArray) {
                    if (list.units !== null) {
                        item.extension.add(
                            Extension()
                                .setUrl(SDC_UNIT_OPTION).setValue(Coding()
                                    .setSystem(UNITS_OF_MEASURE)
                                    .setCode(list.units)
                                )
                        )
                    }
                }
            }
            if (quantity.property !== null && quantity.property.terminologyId !== null && quantity.property.codeString !== null) {
                item.code.add(Coding().setSystem(OPEN_EHR_CODESYSTEM).setCode(quantity.property.codeString))
            }
        }

     }

    private fun     processAttribute(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl?,
        cattribute: CATTRIBUTE,
        ontology: ARCHETYPE?
    ) {

        var item = citem
        var archetype = carchetype

        if (cattribute is CMULTIPLEATTRIBUTEImpl) {
            var attribute: CMULTIPLEATTRIBUTEImpl = cattribute
            if (attribute.cardinality !== null && item != null) {
                if (attribute.cardinality.interval !== null) {
                    if (attribute.cardinality.interval.lower > 0) item.required = true
                    if (attribute.cardinality.interval.upper > 1) item.repeats = true
                    if (attribute.cardinality.interval.upperUnbounded) item.repeats = true
                }
            }
            if (attribute.childrenArray != null) {

                for (children in attribute.childrenArray) {
                    if (children is CARCHETYPEROOTImpl) {
                        processArchetypeObject(item, items, children, ontology)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, archetype,children, ontology)
                    } else if (children is ARCHETYPESLOTImpl) {
                        processArchetypeSlot(item, items, archetype, children)
                    }
                    else {
                        System.out.println(children.javaClass)
                    }

                }
            }
        } else if (cattribute is CSINGLEATTRIBUTEImpl) {
            var attribute: CSINGLEATTRIBUTEImpl = cattribute

            if (attribute.childrenArray != null) {

                for (children in attribute.childrenArray) {
                    if (children is CARCHETYPEROOTImpl) {
                        archetype = children
                        processArchetypeObject( item, items, children, ontology)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject( item, items, archetype, children, ontology)
                    } else if (children is CSINGLEATTRIBUTEImpl) {
                        processSingleObject(item, items,archetype,  children, ontology)
                    } else if (children is CDVQUANTITY) {
                        processQuantity(item, items,archetype,  children)

                    } else if (children is CCODEPHRASEImpl) {
                        processCodePhrase(item, items, archetype, children, ontology)
                    }
                    else if (children is CPRIMITIVEOBJECTImpl) {
                        processPrimative(item, items,archetype,  children, ontology)
                    }
                    else if (children is CDVORDINALImpl) {
                        processOrdinal(item, items, archetype, children, ontology)
                    }
                    else {
                        System.out.println(children.javaClass)
                    }
                }
            }
        } else {
            System.out.println(cattribute.javaClass)
        }
    }



}





