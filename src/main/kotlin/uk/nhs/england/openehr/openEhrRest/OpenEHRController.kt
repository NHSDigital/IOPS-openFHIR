package uk.nhs.england.openehr.openEhrRest

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import io.swagger.v3.oas.annotations.Hidden
import mu.KLogging
import org.apache.xmlbeans.XmlException
import org.hl7.fhir.r4.model.*
import org.openehr.schemas.v1.CATTRIBUTE

import org.openehr.schemas.v1.CDVQUANTITY
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.openehr.schemas.v1.TemplateDocument
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


    @PostMapping("/openehr/\$convertOpenEHRTemplate", produces = ["application/json", "application/xml"])
    fun convert(
        @RequestBody templateXML: String,

        ): String {
        var questionnaire = Questionnaire()
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
            var template = document.template
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
                        processAttribute( null, questionnaire.item, null, attribute)
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
        attribute: CCOMPLEXOBJECTImpl
    ) {
        var item = citem
        if (attribute.nodeId !== null) {
            // This adds in a new child
            var itemId = attribute.nodeId + "-" + Random.nextInt(0, 9999).toString()
            if (citem !== null) itemId = citem.linkId + "/" + itemId

            item = Questionnaire.QuestionnaireItemComponent().setLinkId( itemId)
            var name = "CCOMPLEXOBJECT"
            if (attribute.rmTypeName !== null) name = name + "/" + attribute.rmTypeName
            item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue(name)))

            item.code = getCoding(attribute.nodeId, carchetype)
            item.text = getDisplay(attribute.nodeId, carchetype)
            item.type = Questionnaire.QuestionnaireItemType.STRING
            items.add(item)
        }
        if (item != null && attribute.attributesArray !== null) {
            item.type = Questionnaire.QuestionnaireItemType.GROUP
            for (attribute in attribute.attributesArray) {
                processAttribute(item, item.item,  carchetype, attribute)
            }
        }
    }

    private fun processArchetypeObject(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl
    ) {
        var item = citem
        var archetype = carchetype
        if (archetype.termDefinitionsArray !== null) {

        }

        if (archetype.nodeId !== null) {
            // This adds in a new child
            var itemId = archetype.nodeId + "-" + Random.nextInt(0, 9999).toString()
            if (citem !== null) itemId = citem.linkId + "/" + itemId
            item = Questionnaire.QuestionnaireItemComponent().setLinkId( itemId)

            item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue("CARCHETYPEROOT")))

            item.code = getCoding(archetype.nodeId, archetype)
            item.text = getDisplay(archetype.nodeId, archetype)
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
                    processAttribute(item, item.item, archetype, attribute)
                }
            }
        }
    }

    private fun processSingleObject(
        item: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl?,
        single: CSINGLEATTRIBUTEImpl
    ) {
        System.out.println(single.stringValue)
        if (item != null && single.childrenArray !== null) {
            for (attribute in single.childrenArray) {
               // processAttribute(item, item.item, attribute)
            }
        }
    }


    private fun processOrdinal(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, carchetype: CARCHETYPEROOTImpl?, ordinal: CDVORDINALImpl) {
        if (item != null) {
            System.out.println(ordinal.stringValue)
        }
    }

    private fun processPrimative(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>,  carchetype: CARCHETYPEROOTImpl?,primative: CPRIMITIVEOBJECTImpl) {
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

    private fun processCodePhrase(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, carchetype: CARCHETYPEROOTImpl?, code: CCODEPHRASEImpl) {
        if (item !== null) {
            if (code.codeListArray !== null) {
                item.type= Questionnaire.QuestionnaireItemType.CHOICE
                for (concept in code.codeListArray) {
                    var coding = getCoding(concept,carchetype)
                    var code = Coding().setCode(concept).setDisplay(getDisplay(concept, carchetype))
                    if (coding.size >0 ) {
                        code.setSystem(coding[0].system)
                        code.setCode(coding[0].code)
                    }
                    item.answerOption.add(Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                      code))
                    // removed setSystem(OPEN_EHR_CODESYSTEM). as this didn't make sense
                }
            }
        }
    }

    private fun getDisplay(code: String, archetype: CARCHETYPEROOTImpl?) : String {
        var display =""

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

    private fun getCoding(nodeId: String , archetype: CARCHETYPEROOTImpl?) : List<Coding> {
        val code = ArrayList<Coding>()
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


        if (archetype.nodeId !== null) {
            // This adds in a new child
            var itemId = archetype.nodeId + "-" + Random.nextInt(0, 9999).toString()
            if (citem !== null) itemId = citem.linkId + "/" + itemId
            item = Questionnaire.QuestionnaireItemComponent().setLinkId(itemId)
            item.extension.add(Extension().setUrl(OPEN_EHR_DATATYPE).setValue(StringType().setValue("CARCHETYPEROOT")))

            item.code = getCoding(archetype.nodeId, carchetype)
            item.text = getDisplay(archetype.nodeId, carchetype)

            item.type = Questionnaire.QuestionnaireItemType.DISPLAY
            items.add(item)
        }


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

    private fun processAttribute(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        carchetype: CARCHETYPEROOTImpl?,
        cattribute: CATTRIBUTE
    ) {

        var item = citem
        var archetype = carchetype

        if (cattribute is CMULTIPLEATTRIBUTEImpl) {
            var attribute: CMULTIPLEATTRIBUTEImpl = cattribute

            if (attribute.childrenArray != null) {

                for (children in attribute.childrenArray) {
                    if (children is CARCHETYPEROOTImpl) {
                        processArchetypeObject(item, items, children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, archetype,children)
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
                        processArchetypeObject( item, items, children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject( item, items, archetype, children)
                    } else if (children is CSINGLEATTRIBUTEImpl) {
                        processSingleObject(item, items,archetype,  children)
                    } else if (children is CDVQUANTITY) {
                        processQuantity(item, items,archetype,  children)

                    } else if (children is CCODEPHRASEImpl) {
                        processCodePhrase(item, items, archetype, children)
                    }
                    else if (children is CPRIMITIVEOBJECTImpl) {
                        processPrimative(item, items,archetype,  children)
                    }
                    else if (children is CDVORDINALImpl) {
                        processOrdinal(item, items, archetype, children)
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





