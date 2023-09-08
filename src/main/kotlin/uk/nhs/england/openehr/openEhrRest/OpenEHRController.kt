package uk.nhs.england.openehr.openEhrRest

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import io.swagger.v3.oas.annotations.Hidden
import mu.KLogging
import org.apache.xmlbeans.XmlException
import org.hl7.fhir.r4.model.*
import org.openehr.schemas.v1.CATTRIBUTE
import org.openehr.schemas.v1.CCODEPHRASE
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
import javax.xml.namespace.QName


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
                        processAttribute(null, questionnaire.item, attribute)
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
        attribute: CCOMPLEXOBJECTImpl
    ) {
        var item = citem
        if (attribute.nodeId !== null) {
            // This adds in a new child
            item = Questionnaire.QuestionnaireItemComponent().setLinkId(attribute.nodeId)
            items.add(item)
        }
        if (item != null && attribute.attributesArray !== null) {
            for (attribute in attribute.attributesArray) {
                processAttribute(item, item.item, attribute)
            }
        }
    }

    private fun processArchetypeObject(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        archetype: CARCHETYPEROOTImpl
    ) {
        var item = citem
        if (archetype.termDefinitionsArray !== null) {

        }

        if (archetype.nodeId !== null) {
            // This adds in a new child
            item = Questionnaire.QuestionnaireItemComponent().setLinkId(archetype.nodeId)
            items.add(item)
        }
        if (item != null) {
            if (archetype.archetypeId !== null) {
                item.extension.add(
                    Extension().setUrl(OPEN_EHR_ARCHETYPE).setValue(StringType().setValue(archetype.archetypeId.value))
                )
            }
            if (archetype.attributesArray !== null) {
                for (attribute in archetype.attributesArray) {
                    processAttribute(item, item.item, attribute)
                }
            }
        }
    }

    private fun processSingleObject(
        item: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        single: CSINGLEATTRIBUTEImpl
    ) {
        if (item != null && single.childrenArray !== null) {
            for (attribute in single.childrenArray) {
               // processAttribute(item, item.item, attribute)
            }
        }
    }


    private fun processOrdinal(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, ordinal: CDVORDINALImpl) {

    }

    private fun processPrimative(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, primative: CPRIMITIVEOBJECTImpl) {

    }

    private fun processCodePhrase(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, code: CCODEPHRASEImpl) {
        if (item !== null) {
            if (code.codeListArray !== null) {

                for (concept in code.codeListArray) {
                    item.answerOption.add(Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                        Coding().setSystem(OPEN_EHR_CODESYSTEM).setCode(concept))
                    )
                }
            }
        }
    }

    private fun processArchetypeSlot(item: Questionnaire.QuestionnaireItemComponent?, items: MutableList<Questionnaire.QuestionnaireItemComponent>, children: ARCHETYPESLOTImpl) {

    }

    private fun processQuantity(item: Questionnaire.QuestionnaireItemComponent?,
                                items: MutableList<Questionnaire.QuestionnaireItemComponent>, quantity: CDVQUANTITY) {
        if (item !== null) {
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
        cattribute: CATTRIBUTE
    ) {

        var item = citem

        if (cattribute is CMULTIPLEATTRIBUTEImpl) {
            var attribute: CMULTIPLEATTRIBUTEImpl = cattribute

            if (attribute.childrenArray != null) {

                for (children in attribute.childrenArray) {
                    if (children is CARCHETYPEROOTImpl) {
                        processArchetypeObject(item, items, children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, children)
                    } else if (children is ARCHETYPESLOTImpl) {
                        processArchetypeSlot(item, items, children)
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
                        processArchetypeObject(item, items, children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, children)
                    } else if (children is CSINGLEATTRIBUTEImpl) {
                        processSingleObject(item, items, children)
                    } else if (children is CDVQUANTITY) {
                        processQuantity(item, items, children)

                    } else if (children is CCODEPHRASEImpl) {
                        processCodePhrase(item, items, children)
                    }
                    else if (children is CPRIMITIVEOBJECTImpl) {
                        processPrimative(item, items, children)
                    }
                    else if (children is CDVORDINALImpl) {
                        processOrdinal(item, items, children)
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





