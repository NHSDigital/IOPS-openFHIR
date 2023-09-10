package uk.nhs.england.openehr.model

import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent
import org.openehr.schemas.v1.ARCHETYPE
import org.openehr.schemas.v1.CATTRIBUTE
import org.openehr.schemas.v1.CDVQUANTITY
import org.openehr.schemas.v1.OPERATIONALTEMPLATE
import org.openehr.schemas.v1.impl.*
import uk.nhs.england.openehr.util.FhirSystems
import uk.nhs.england.openehr.util.FhirSystems.OPENEHR_TEMPLATE
import kotlin.random.Random

class openEHRtoFHIR {
    var template: OPERATIONALTEMPLATE? = null
    var archeType: ARCHETYPE? = null;
    var rootArchetype : CARCHETYPEROOTImpl? = null;
    val questionnaire = Questionnaire()
    init {
        questionnaire.status= Enumerations.PublicationStatus.DRAFT
    }

    constructor(template: OPERATIONALTEMPLATE) {
        this.template = template
        if (template.templateId !== null) {
            this.questionnaire.url = OPENEHR_TEMPLATE + "/" + template.templateId.value
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
                if (attribute.rmAttributeName !== null) {
                    if (attribute.rmAttributeName.equals("content")) {
                        processAttribute(null, questionnaire.item, attribute)
                    }
                    if (attribute.rmAttributeName.equals("context")) {
                        var item = QuestionnaireItemComponent().setLinkId("context")
                            .setType(Questionnaire.QuestionnaireItemType.GROUP)
                            .setText("Other context")
                            .setRequired(false)

                        processAttribute(item, questionnaire.item, attribute)
                        // if no context then don't include
                        if (item.item.size>0) questionnaire.item.add(item)
                    }
                    if (attribute.rmAttributeName.equals("name")) {
                        processName(attribute)
                    }
                }
                //            processAttribute(questionnaire.item,attribute)
            }
        }
    }

    constructor(archetype: ARCHETYPE) {
        this.archeType = archetype

        if (archetype.archetypeId !== null) {
            questionnaire.url = "https://example.fhir.openehr.org/Questionnaire/" + archetype.archetypeId.value
            questionnaire.url = questionnaire.url.replace(" ", "")
            questionnaire.title = archetype.archetypeId.value
        }
        if (archetype.uid !== null) {
            questionnaire.identifier.add(Identifier().setValue(archetype.uid.value))
        }
        if (archetype.concept !== null) {
            questionnaire.title = getDisplay(archetype.concept.toString())
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
                processAttribute( null, questionnaire.item,  attribute)
            }
        }
    }


    private fun processName(attribute: CATTRIBUTE) {
        /// WTF
        if (attribute.childrenArray !== null) {
            for (child in attribute.childrenArray) {
                if (child is CCOMPLEXOBJECTImpl) {
                    val complex: CCOMPLEXOBJECTImpl = child
                    if (complex.attributesArray !== null) {
                        for (attrib in complex.attributesArray) {
                            if (attrib is CSINGLEATTRIBUTEImpl) {
                                val single: CSINGLEATTRIBUTEImpl = attrib
                                if (single.childrenArray !== null) {
                                    for (singlechild in single.childrenArray) {
                                        if (singlechild is CPRIMITIVEOBJECTImpl) {
                                            val primative : CPRIMITIVEOBJECTImpl = singlechild
                                            if (primative.item !== null && primative.item is CSTRINGImpl) {
                                                val str = primative.item as CSTRINGImpl
                                                if (str.listArray !== null && str.listArray.size>0) {
                                                    questionnaire.title = str.listArray[0]
                                                }

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                        this.rootArchetype = children
                        processArchetypeRoot(item, items)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, children)
                    } else if (children is ARCHETYPESLOTImpl) {
                        processArchetypeSlot(item, children)
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
                        this.rootArchetype = children
                        processArchetypeRoot( item, items)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject( item, items, children)
                    } else if (children is CSINGLEATTRIBUTEImpl) {
                        processSingleObject(item, children)
                    } else if (children is CDVQUANTITY) {
                        processQuantity(item, children)

                    } else if (children is CCODEPHRASEImpl) {
                        processCodePhrase(item, children)
                    }
                    else if (children is CPRIMITIVEOBJECTImpl) {
                        processPrimative(item, children)
                    }
                    else if (children is CDVORDINALImpl) {
                        processOrdinal(item,  children)
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

    private fun processArchetypeRoot(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>
    ) {
        var item = citem
        if (rootArchetype !== null) {
            if (rootArchetype !== null && rootArchetype!!.nodeId !== null) {
                // This adds in a new child
                var itemId = rootArchetype!!.nodeId + "-" + Random.nextInt(0, 9999).toString()
                if (citem !== null) itemId = citem.linkId + "/" + itemId
                item = Questionnaire.QuestionnaireItemComponent().setLinkId(itemId)

                item.extension.add(
                    Extension().setUrl(FhirSystems.OPENEHR_DATATYPE_EXT).setValue(StringType().setValue("CARCHETYPEROOT"))
                )

                item.code = getCoding(rootArchetype!!.nodeId)
                item.text = getDisplay(rootArchetype!!.nodeId)
                item.type = Questionnaire.QuestionnaireItemType.STRING
                items.add(item)
            }

            if (item != null) {

                if (rootArchetype!!.archetypeId !== null) {
                    item.extension.add(
                        Extension().setUrl(FhirSystems.OPENEHR_ARCHETYPE_EXT)
                            .setValue(UriType().setValue(OPENEHR_TEMPLATE + "/" + rootArchetype!!.archetypeId.value))
                    )
                }
                if (this.rootArchetype!!.attributesArray !== null) {
                    item.type = Questionnaire.QuestionnaireItemType.GROUP
                    for (attribute in rootArchetype!!.attributesArray) {
                        processAttribute(item, item.item, attribute)
                    }
                }
            }
        }
    }

    private fun processComplexObject(
        citem: Questionnaire.QuestionnaireItemComponent?,
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        attribute: CCOMPLEXOBJECTImpl,
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
                    item.extension.add(Extension().setUrl(FhirSystems.OPENEHR_DATATYPE_EXT).setValue(StringType().setValue(name)))

                    item.code = getCoding(attribute.nodeId)
                    var addSDC = false
                    for(concept in item.code) {
                        if (concept.system !== null && (
                                    concept.system.equals(FhirSystems.SNOMED_CT) || concept.system.equals(FhirSystems.LOINC)
                                    )) addSDC = true
                    }
                    if (addSDC) {
                        item.extension.add(Extension()
                            .setUrl(FhirSystems.SDC_PERIOD)
                            .setValue(Duration()
                                .setValue(2)
                                .setCode("wk")
                                .setUnit("weeks")
                                .setSystem(FhirSystems.UNITS_OF_MEASURE))
                        )
                        item.extension.add(Extension()
                            .setUrl(FhirSystems.SDC_EXTRACT)
                            .setValue(BooleanType().setValue(true))
                        )
                    }
                    item.text = getDisplay(attribute.nodeId)
                    item.type = Questionnaire.QuestionnaireItemType.STRING
                    items.add(item)
                } else {
                 //   System.out.println(attribute.rmTypeName)
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
                    processAttribute(item, item.item,  attr)
                }
            } else {
                for (attr in attribute.attributesArray) {
                    processAttribute(item, items,  attr)
                }
            }
        }
        if (item != null) {
            if (attribute.rmTypeName.equals("ITEM_TREE")) {
                item.type = Questionnaire.QuestionnaireItemType.GROUP
            } else if (attribute.rmTypeName.equals("DV_TEXT")) {
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
            } else if (attribute.rmTypeName.equals("DV_COUNT")) {
                item.type = Questionnaire.QuestionnaireItemType.INTEGER
            } else if (attribute.rmTypeName.equals("DV_CODED_TEXT")) {
                item.type = Questionnaire.QuestionnaireItemType.OPENCHOICE
            } else if (attribute.rmTypeName.equals("DV_DATE_TIME")) {
                item.type = Questionnaire.QuestionnaireItemType.DATETIME
            } else if (attribute.rmTypeName.equals("DV_BOOLEAN")) {
                item.type = Questionnaire.QuestionnaireItemType.BOOLEAN
            } else if (attribute.rmTypeName.equals("DV_IDENTIFIER")) {
                item.type = Questionnaire.QuestionnaireItemType.STRING
            } else if (attribute.rmTypeName.equals("CLUSTER")
                || attribute.rmTypeName.equals("ELEMENT")
                || attribute.rmTypeName.equals("EVENT")
                || attribute.rmTypeName.equals("EVENT_CONTEXT")
                || attribute.rmTypeName.equals("ACTIVITY")
                || attribute.rmTypeName.equals("HISTORY")
                || attribute.rmTypeName.equals("ISM_TRANSITION")
            ) {
              // Ignore  item.type = Questionnaire.QuestionnaireItemType.DATETIME
            } else {
                System.out.println("Complex Object - Unknown data type " + attribute.rmTypeName)
            }


        }
    }


    private fun processSingleObject(
        item: QuestionnaireItemComponent?,
        single: CSINGLEATTRIBUTEImpl

    ) {
        //   System.out.println(single.stringValue)
        if (item != null && single.childrenArray !== null) {
            for (attribute in single.childrenArray) {
                if (attribute is CPRIMITIVEOBJECTImpl) {
                    processPrimative(item,  attribute)
                }
            }
        }
    }


    private fun processQuantity(item: Questionnaire.QuestionnaireItemComponent?,
                                quantity: CDVQUANTITY) {
        if (item !== null) {
            item.type = Questionnaire.QuestionnaireItemType.QUANTITY
            if (quantity.listArray !== null) {
                for (list in quantity.listArray) {
                    if (list.units !== null) {
                        item.extension.add(
                            Extension()
                                .setUrl(FhirSystems.SDC_UNIT_OPTION).setValue(
                                    Coding()
                                    .setSystem(FhirSystems.UNITS_OF_MEASURE)
                                    .setCode(list.units)
                                )
                        )
                    }
                }
            }
            if (quantity.property !== null && quantity.property.terminologyId !== null && quantity.property.codeString !== null) {
                item.code.add(Coding().setSystem(FhirSystems.OPENEHR_CODESYSTEM).setCode(quantity.property.codeString))
            }
        }

    }

    private fun processOrdinal(item: Questionnaire.QuestionnaireItemComponent?,
                               ordinal: CDVORDINALImpl) {
        if (item != null) {
            item.type = Questionnaire.QuestionnaireItemType.CHOICE
            if (ordinal.listArray != null) {
                for (ord in ordinal.listArray) {

                    var code = Coding()
                    if (ord.symbol != null
                        && ord.symbol.definingCode !== null
                        && ord.symbol.definingCode.codeString !== null) {
                        code.code = ord.symbol.definingCode.codeString
                        if (archeType != null) {
                            code.system = FhirSystems.OPENEHR_CODESYSTEM + "/" + archeType!!.archetypeId.value
                        }
                        code.display = getDisplay(ord.symbol.definingCode.codeString)
                    }
                    // TODO add value as score
                    var answerOption = Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                        code
                    )
                    answerOption.extension.add(
                        Extension().setUrl(FhirSystems.SDC_ORDINAL_VALUE).setValue(DecimalType().setValue(ord.value.toBigDecimal()))
                    )
                    item.addAnswerOption(answerOption)
                }
            }
            // System.out.println(ordinal.stringValue)
        }
    }

    private fun processPrimative(item: Questionnaire.QuestionnaireItemComponent?,
                                 primative: CPRIMITIVEOBJECTImpl,
                                ) {
        if (item != null && primative.item != null && primative.rmTypeName != null) {
            if (primative.item != null){
                if (primative.item is CSTRINGImpl) {
                    val str = primative.item as CSTRINGImpl
                    if (str.listArray !== null && str.listArray.size>0) {
                        item.text = str.listArray[0]
                    }
                }
            }
            when (primative.rmTypeName) {
                "INTEGER" -> item.type = Questionnaire.QuestionnaireItemType.INTEGER
                "REAL" -> item.type = Questionnaire.QuestionnaireItemType.DECIMAL
                "BOOLEAN" -> item.type = Questionnaire.QuestionnaireItemType.BOOLEAN
                "STRING" -> {
                    item.type = Questionnaire.QuestionnaireItemType.STRING

                }
                else -> { // Note the block
                    throw UnprocessableEntityException("Unsupported type " + primative.rmTypeName)
                }
            }
        }
    }

    private fun processCodePhrase(item: Questionnaire.QuestionnaireItemComponent?,
                                  code: CCODEPHRASEImpl) {
        if (item !== null) {
            if (code.codeListArray !== null) {
                item.type= Questionnaire.QuestionnaireItemType.CHOICE
                for (concept in code.codeListArray) {
                    if (code.terminologyId.value.equals("openehr")) {
                        item.answerValueSet = FhirSystems.OPENEHR_VALUESET + "/" + concept
                    } else {
                        var coding = getCoding(concept)
                        var code = Coding().setCode(concept).setDisplay(getDisplay(concept))
                        if (coding.size > 0) {
                            code.setSystem(coding[0].system)
                            code.setCode(coding[0].code)
                        } else {
                       // TODO Revisit and see if can be fixed     System.out.println("Unable to find " + concept)
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

    private fun processArchetypeSlot(citem: Questionnaire.QuestionnaireItemComponent?,
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



    private fun getDisplay(code: String) : String {
        var display = code
        if (this.archeType != null && this.archeType!!.ontology != null && this.archeType!!.ontology.termDefinitionsArray !== null) {
            for (terms in this.archeType!!.ontology.termDefinitionsArray) {
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
        if (rootArchetype != null && rootArchetype!!.termDefinitionsArray !== null) {
            for (term in rootArchetype!!.termDefinitionsArray) {
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

    private fun getCoding(nodeId: String ) : List<Coding> {
        val code = ArrayList<Coding>()
        if (archeType != null && archeType!!.ontology != null) {
            if (archeType!!.ontology.termBindingsArray != null) {
                for (termBinding in archeType!!.ontology.termBindingsArray) {
                    if (termBinding.terminology.equals("LOINC")) {
                        if (termBinding.itemsArray != null) {
                            for (codes in termBinding.itemsArray) {
                                if (codes.code.equals(nodeId)) code.add(
                                    Coding().setSystem(FhirSystems.LOINC).setCode(codes.value.codeString)
                                )
                            }
                        }
                    }
                    if (termBinding.terminology.equals("SNOMED-CT")) {
                        if (termBinding.itemsArray != null) {
                            for (codes in termBinding.itemsArray) {
                                if (codes.code.equals(nodeId)) code.add(
                                    Coding().setSystem(FhirSystems.SNOMED_CT).setCode(codes.value.codeString)
                                )
                            }
                        }
                    }
                }
            }
            if (archeType!!.ontology.termDefinitionsArray != null) {
                for (termBinding in archeType!!.ontology.termDefinitionsArray) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) for (item in codes.itemsArray) {
                                if (item.id.equals("text")) {
                                    code.add(
                                        Coding().setCode(nodeId).setDisplay(item.stringValue).setSystem(
                                            FhirSystems.OPENEHR_CODESYSTEM + "/" + archeType!!.archetypeId.value))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (rootArchetype != null && rootArchetype!!.termBindingsArray != null) {
            for (termBinding in rootArchetype!!.termBindingsArray) {
                if (termBinding.terminology.equals("LOINC")) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) code.add(Coding().setSystem(FhirSystems.LOINC).setCode(codes.value.codeString))
                        }
                    }
                }
                if (termBinding.terminology.equals("SNOMED-CT")) {
                    if (termBinding.itemsArray != null) {
                        for (codes in termBinding.itemsArray) {
                            if (codes.code.equals(nodeId)) code.add(Coding().setSystem(FhirSystems.SNOMED_CT).setCode(codes.value.codeString))
                        }
                    }
                }
            }
        }
        return code
    }



}
