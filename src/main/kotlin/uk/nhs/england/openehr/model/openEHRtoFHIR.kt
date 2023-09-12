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
import uk.nhs.england.openehr.util.FhirSystems.*
import kotlin.random.Random

class openEHRtoFHIR {
    var template: OPERATIONALTEMPLATE? = null
    var archeType: ARCHETYPE? = null;
    //var rootArchetype : CARCHETYPEROOTImpl? = null;
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
                for (details in template.description.detailsArray) {
                    if (details.language === null || (details.language.codeString.equals("en"))) {
                        questionnaire.purpose = details.purpose
                        questionnaire.description = details.use
                    }
                }
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
                        processAttribute(null, questionnaire.item, attribute,null)
                    }
                    if (attribute.rmAttributeName.equals("context")) {
                        var item = QuestionnaireItemComponent().setLinkId("context")
                            .setType(Questionnaire.QuestionnaireItemType.GROUP)
                            .setText("Other context")
                            .setRequired(false)

                        processAttribute(item, questionnaire.item, attribute, null)
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
            questionnaire.url = OPENEHR_TEMPLATE +"/" + archetype.archetypeId.value
            questionnaire.url = questionnaire.url.replace(" ", "")
            questionnaire.title = archetype.archetypeId.value
        }
        if (archetype.uid !== null) {
            questionnaire.identifier.add(Identifier().setValue(archetype.uid.value))
        }
        if (archetype.concept !== null) {
            questionnaire.title = getDisplay(archetype.concept.toString(),null)
        }
        if (archetype.description !== null) {
            if (archetype.description.detailsArray !== null) {
                for (details in archetype.description.detailsArray) {
                    if (details.language === null || (details.language.codeString.equals("en"))) {
                        questionnaire.purpose = details.purpose
                        questionnaire.description = details.use
                    }
                }
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
                processAttribute( null, questionnaire.item,  attribute, null)
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
        cattribute: CATTRIBUTE,
        crootArchetype : CARCHETYPEROOTImpl?
    ) {
        var rootArchetype = crootArchetype
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
                        processArchetypeRoot(item, items,children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject(item, items, children, rootArchetype)
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

                        processArchetypeRoot( item, items, children)
                    } else if (children is CCOMPLEXOBJECTImpl) {
                        processComplexObject( item, items, children, rootArchetype)
                    } else if (children is CSINGLEATTRIBUTEImpl) {
                        processSingleObject(item, children)
                    } else if (children is CDVQUANTITY) {
                        processQuantity(item, children)

                    } else if (children is CCODEPHRASEImpl) {
                        processCodePhrase(item, children, rootArchetype)
                    }
                    else if (children is CPRIMITIVEOBJECTImpl) {
                        processPrimative(item, children)
                    }
                    else if (children is CDVORDINALImpl) {
                        processOrdinal(item,  children, rootArchetype)
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
        items: MutableList<Questionnaire.QuestionnaireItemComponent>,
        rootArchetype : CARCHETYPEROOTImpl?
    ) {
        var item = citem
        if (rootArchetype !== null) {
            if (rootArchetype !== null && rootArchetype!!.nodeId !== null) {
                // This adds in a new child

                /* OLD
                var itemId = rootArchetype!!.nodeId + "-" + Random.nextInt(0, 9999).toString()
                if (citem !== null) itemId = citem.linkId + "/" + itemId
                item = Questionnaire.QuestionnaireItemComponent().setLinkId(itemId)

                 */

                var itemId = rootArchetype.nodeId
                if (rootArchetype.archetypeId !== null) itemId = rootArchetype.archetypeId.value
                if (citem !== null) itemId = citem.linkId + "/" + itemId
                item = Questionnaire.QuestionnaireItemComponent().setLinkId(itemId)


                item.extension.add(
                    Extension().setUrl(FhirSystems.OPENEHR_DATATYPE_EXT).setValue(StringType().setValue("CARCHETYPEROOT"))
                )

                item.code = getCoding(rootArchetype.nodeId,rootArchetype)
                item.text = getDisplay(rootArchetype.nodeId,rootArchetype)
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
                if (rootArchetype!!.attributesArray !== null) {
                    item.type = Questionnaire.QuestionnaireItemType.GROUP
                    for (attribute in rootArchetype!!.attributesArray) {
                        processAttribute(item, item.item, attribute,rootArchetype)
                    }
                }
            }
        }
        if (item != null && item.type == Questionnaire.QuestionnaireItemType.GROUP && !item.hasItem()) {

            item.type = Questionnaire.QuestionnaireItemType.DISPLAY
        }
    }

    private fun processComplexObject(
        citem: QuestionnaireItemComponent?,
        items: MutableList<QuestionnaireItemComponent>,
        attribute: CCOMPLEXOBJECTImpl,
        rootArchetype : CARCHETYPEROOTImpl?
    ) {
        var item = citem
        if (attribute.nodeId !== null) {
            // This adds in a new child
            if (attribute.rmTypeName !== null) {
                if (
                    attribute.rmTypeName.equals("ELEMENT")
                   || attribute.rmTypeName.equals("CLUSTER")
                ) {
                    /* OLD
                    var itemId = attribute.nodeId + "-" + Random.nextInt(0, 9999).toString()
                    if (citem !== null) itemId = citem.linkId + "/" + itemId
                    */

                    var itemId = attribute.nodeId
                    if (citem !== null) itemId = citem.linkId + "/" + itemId

                    item = Questionnaire.QuestionnaireItemComponent().setLinkId( itemId)
                    var name = "CCOMPLEXOBJECT"
                    if (attribute.rmTypeName !== null) name = name + "/" + attribute.rmTypeName
                    item.extension.add(Extension().setUrl(FhirSystems.OPENEHR_DATATYPE_EXT).setValue(StringType().setValue(name)))

                    item.code = getCoding(attribute.nodeId,rootArchetype)
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
                    item.text = getDisplay(attribute.nodeId,rootArchetype)
                    if (attribute.rmTypeName.equals("CLUSTER")) {
                        item.type = Questionnaire.QuestionnaireItemType.GROUP
                    } else {
                        item.type = Questionnaire.QuestionnaireItemType.STRING
                    }
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
                    processAttribute(item, item.item, attr, rootArchetype)
                }
            } else {
                for (attr in attribute.attributesArray) {
                    processAttribute(item, items, attr,rootArchetype)
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
                           // item.type = Questionnaire.QuestionnaireItemType.TEXT
                            //System.out.println("Is a GROUP - DV_TEXT")
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
            } else if (attribute.rmTypeName.equals("DV_PROPORTION")) {
                item.type = Questionnaire.QuestionnaireItemType.DECIMAL
            } else if (attribute.rmTypeName.equals("POINT_EVENT")) {
                System.out.println("Not processing POINT EVENT")
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
                        if (list.magnitude !== null) {
                            if (list.magnitude.lower !== null) {
                                item.extension.add(Extension(SDC_QTY_MIN).setValue(Quantity().setCode(list.units).setValue(list.magnitude.lower.toDouble())))
                            }
                            if (list.magnitude.upper !== null) {
                                item.extension.add(Extension(SDC_QTY_MAX).setValue(Quantity().setCode(list.units).setValue(list.magnitude.upper.toDouble())))
                            }
                        }
                    }
                }
            }
            if (quantity.property !== null && quantity.property.terminologyId !== null && quantity.property.codeString !== null) {
                item.code.add(Coding().setSystem(FhirSystems.OPENEHR_CODESYSTEM).setCode(quantity.property.codeString))
            }
        }

    }

    private fun processOrdinal(item: Questionnaire.QuestionnaireItemComponent?,
                               ordinal: CDVORDINALImpl,
                               rootArchetype : CARCHETYPEROOTImpl?) {
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
                        code.display = getDisplay(ord.symbol.definingCode.codeString, rootArchetype)
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
                                 primative: CPRIMITIVEOBJECTImpl
                                ) {
        if (item != null && primative.item != null && primative.rmTypeName != null) {
            if (item.type === Questionnaire.QuestionnaireItemType.GROUP
                || item.type === Questionnaire.QuestionnaireItemType.QUANTITY
                || item.type === Questionnaire.QuestionnaireItemType.STRING) {
                if (primative.item != null){
                    if (primative.item is CSTRINGImpl) {
                        val str = primative.item as CSTRINGImpl
                        if (str.listArray !== null && str.listArray.size>0) {
                            for (answer in str.listArray) {
                                val type = getStringCoded(answer)
                                if (type is StringType) {
                                    item.text = type.value
                                }
                                if (type is Coding) {
                                    item.text = type.display
                                    item.code.add(type)
                                }
                            }
                        }
                    }
                }
            } else {
                if (primative.item != null){
                    if (primative.item is CSTRINGImpl) {
                        val str = primative.item as CSTRINGImpl
                        if (str.listArray !== null && str.listArray.size>0) {
                            for (answer in str.listArray) {
                                item.addAnswerOption(Questionnaire.QuestionnaireItemAnswerOptionComponent().setValue(
                                    getStringCoded(answer)))
                            }
                        }
                    }
                    if (primative.item is CREALImpl) {
                        var real = primative.item as CREALImpl
                        if (real.range != null) {
                            if (real.range.lower !== null) {

                            }
                            if (real.range.lower !== null) {
                                item.extension.add(Extension(SDC_QTY_MIN).setValue(Quantity().setValue(real.range.lower.toDouble())))
                            }
                            if (real.range.upper !== null && real.range.upper > 0) {
                                item.extension.add(Extension(SDC_QTY_MAX).setValue(Quantity().setValue(real.range.upper.toDouble())))
                            }
                        }
                    }
                }
                if (item.type === Questionnaire.QuestionnaireItemType.GROUP) {
                    // check if this is used
                    when (primative.rmTypeName) {
                        "INTEGER" -> item.type = Questionnaire.QuestionnaireItemType.INTEGER
                        "REAL" -> {
                            item.type = Questionnaire.QuestionnaireItemType.DECIMAL
                            primative.item
                        }
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
        }
    }

    private fun getStringCoded(answer : String) : Type? {
        var coded = answer.split("::")
        if (coded.size<2) {
            return StringType().setValue(answer)
        } else {
            var code = Coding().setCode(coded[1]).setDisplay(coded[2])
            when (coded[0]) {
                "LOINC" -> {
                    code.system = LOINC
                }
                "SNOMED-CT(2003)", "SNOMED-CT" -> {
                    code.system = SNOMED_CT
                }
            }
            return code
        }
    }


    private fun processCodePhrase(item: Questionnaire.QuestionnaireItemComponent?,
                                  code: CCODEPHRASEImpl,
                                  rootArchetype : CARCHETYPEROOTImpl?) {
        if (item !== null) {
            if (code.codeListArray !== null) {
                item.type= Questionnaire.QuestionnaireItemType.CHOICE
                for (concept in code.codeListArray) {
                    if (code.terminologyId.value.equals("openehr")) {
                        item.answerValueSet = FhirSystems.OPENEHR_VALUESET + "/" + concept
                    } else {
                        var coding = getCoding(concept, rootArchetype)
                        var code = Coding().setCode(concept).setDisplay(getDisplay(concept,rootArchetype))
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



    private fun getDisplay(code: String, rootArchetype : CARCHETYPEROOTImpl?) : String {
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
        } else
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
        } else {
            System.out.println("Should be impossible")
        }
        return display
    }

    private fun getCoding(nodeId: String,rootArchetype : CARCHETYPEROOTImpl? ) : List<Coding> {

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
                for (termDefinition in archeType!!.ontology.termDefinitionsArray) {
                    if (termDefinition.language == null || termDefinition.language.equals("en"))
                    if (termDefinition.itemsArray != null) {
                        for (codes in termDefinition.itemsArray) {
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
        else if (rootArchetype != null && rootArchetype!!.termBindingsArray != null) {
            for (termBinding in rootArchetype!!.termBindingsArray) {
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
        } else {
            System.out.println("Should be impossible")
        }
        return code
    }



}
