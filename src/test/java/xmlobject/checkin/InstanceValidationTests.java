/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package xmlobject.checkin;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.junit.jupiter.api.Test;
import tools.util.JarUtil;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InstanceValidationTests {
    private SchemaTypeLoader makeSchemaTypeLoader(String[] schemas) throws XmlException {
        XmlObject[] schemaDocs = new XmlObject[schemas.length];

        for (int i = 0; i < schemas.length; i++) {
            schemaDocs[i] = XmlObject.Factory.parse(schemas[i]);
        }

        return XmlBeans.loadXsd(schemaDocs);
    }

    private SchemaTypeLoader makeSchemaTypeLoader(File[] schemas) throws XmlException, IOException {
        XmlObject[] schemaDocs = new XmlObject[schemas.length];

        for (int i = 0; i < schemas.length; i++) {
            schemaDocs[i] = XmlObject.Factory.parse(schemas[i], new XmlOptions().setLoadLineNumbers().setLoadMessageDigest());
        }

        return XmlBeans.loadXsd(schemaDocs);
    }


    private List<XmlError> performValidation(String[] schemas, String instances) throws XmlException {
        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        XmlOptions options = new XmlOptions();

        XmlObject x = stl.parse(instances, null, options);

        List<XmlError> xel = new ArrayList<>();
        options.setErrorListener(xel);

        x.validate(options);

        return xel;
    }

    @Test
    void testValidationElementError() throws XmlException {
        String bobSchema = "<xs:schema\n" + "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" + "   xmlns:bob='http://openuri.org/bobschema'\n" + "   targetNamespace='http://openuri.org/bobschema'\n" + "   elementFormDefault='qualified'>\n" + "\n" + "  <xs:complexType name='biff'>\n" + "   <xs:complexContent>\n" + "    <xs:extension base='bob:foo'>\n" + "     <xs:sequence>\n" + "       <xs:element name='a' minOccurs='0' maxOccurs='unbounded'/>\n" + "     </xs:sequence>\n" + "    </xs:extension>\n" + "   </xs:complexContent>\n" + "  </xs:complexType>\n" + "" + "  <xs:complexType name='foo'>\n" + "  </xs:complexType>\n" + "" + "  <xs:element name='foo' type='bob:foo'>\n" + "  </xs:element>\n" + "" + "</xs:schema>\n";

        String invalid = "<bob:foo xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:bob='http://openuri.org/bobschema' " + "xsi:type='bob:biff'><bob:q/></bob:foo>";

        String[] schemas = {bobSchema};

        List<XmlError> errors = performValidation(schemas, invalid);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        for (Object error : errors) {
            XmlValidationError xmlValError = (XmlValidationError) error;
            assertEquals(xmlValError.getErrorType(), XmlValidationError.INCORRECT_ELEMENT);
            assertEquals(xmlValError.getBadSchemaType().getName().getLocalPart(), "biff");
            assertEquals(xmlValError.getOffendingQName().getLocalPart(), "q");
            assertEquals(xmlValError.getMessage(), "Expected element 'a@http://openuri.org/bobschema' instead of 'q@http://openuri.org/bobschema' here in element foo@http://openuri.org/bobschema");
        }
    }

    @Test
    void testValidationAttributeError() throws XmlException {

        String empSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'   elementFormDefault='qualified'>\n" +
            "<xs:element name='age'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:integer'>\n" +
            "<xs:minInclusive value='0'/>\n" +
            "<xs:maxInclusive value='100'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:element>\n" +
            "<xs:element name='empRecords'>\n" +
            "<xs:complexType>\n" +
            "<xs:sequence>\n" +
            "<xs:element name='person' type='personType' maxOccurs='unbounded'/>\n" +
            "</xs:sequence>\n" +
            "</xs:complexType>\n" +
            "</xs:element>\n" +
            "<xs:element name='name' type='xs:string'/>\n" +
            "<xs:complexType name='personType'>\n" +
            "<xs:sequence>\n" +
            "<xs:element ref='name'/>\n" +
            "<xs:element ref='age'/>\n" +
            "</xs:sequence>\n" +
            "<xs:attribute name='employee' use='required'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:NMTOKEN'>\n" +
            "<xs:enumeration value='current'/>\n" +
            "<xs:enumeration value='past'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:attribute>\n" +
            "</xs:complexType>\n" +
            "</xs:schema>\n";
        String[] schemas = {empSchema};

        String xmlInstance = "<empRecords xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' >" +
                             "<person employee='past'>" +
                             "<name>joe blow</name>" +
                             "<age>31</age>" +
                             "</person>" +
                             "<person>" +
                             "<name>test user</name>" +
                             "<age>29</age>" +
                             "</person>" +
                             "</empRecords>";
        List<XmlError> errors = performValidation(schemas, xmlInstance);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        for (Object error : errors) {
            XmlValidationError xmlValError = (XmlValidationError) error;
            assertEquals(xmlValError.getErrorType(), XmlValidationError.INCORRECT_ATTRIBUTE);
            assertEquals(xmlValError.getBadSchemaType().getName().getLocalPart(), "personType");
            assertEquals(xmlValError.getOffendingQName().getLocalPart(), "employee");
            assertEquals(xmlValError.getMessage(), "Expected attribute: employee in element person");
        }
    }

    @Test
    void testValidationIncorrectElementError() throws XmlException {

        String empSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'   elementFormDefault='qualified'>\n" +
            "<xs:element name='age'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:integer'>\n" +
            "<xs:minInclusive value='0'/>\n" +
            "<xs:maxInclusive value='100'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:element>\n" +
            "<xs:element name='empRecords'>\n" +
            "<xs:complexType>\n" +
            "<xs:sequence>\n" +
            "<xs:element name='person' type='personType' maxOccurs='unbounded'/>\n" +
            "</xs:sequence>\n" +
            "</xs:complexType>\n" +
            "</xs:element>\n" +
            "<xs:element name='name' type='xs:string'/>\n" +
            "<xs:complexType name='personType'>\n" +
            "<xs:sequence>\n" +
            "<xs:element ref='name'/>\n" +
            "<xs:element ref='age'/>\n" +
            "</xs:sequence>\n" +
            "<xs:attribute name='employee' use='required'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:NMTOKEN'>\n" +
            "<xs:enumeration value='current'/>\n" +
            "<xs:enumeration value='past'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:attribute>\n" +
            "</xs:complexType>\n" +
            "</xs:schema>\n";
        String[] schemas = {empSchema};

        String xmlInstance =
            "<empRecords xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' >" +
            "<person employee='past'>" +
            "<age>31</age>" +
            "</person>" +
            "<person employee='current'>" +
            "<name>test user</name>" +
            "<age>29</age>" +
            "</person>" +
            "</empRecords>";

        List<XmlError> errors = performValidation(schemas, xmlInstance);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        Iterator<XmlError> it = errors.iterator();
        assertTrue(it.hasNext());

        XmlValidationError xmlValError = (XmlValidationError) it.next();
        assertEquals(XmlValidationError.INCORRECT_ELEMENT, xmlValError.getErrorType());
        assertEquals("personType", xmlValError.getBadSchemaType().getName().getLocalPart());
        // todo debug this Assert.assertEquals(xmlValError.getOffendingQName().getLocalPart(), "age");
        assertEquals("Expected element 'name' instead of 'age' here in element person", xmlValError.getMessage());

        assertTrue(it.hasNext());

        xmlValError = (XmlValidationError) it.next();
        assertEquals(XmlValidationError.INCORRECT_ELEMENT, xmlValError.getErrorType());
        assertEquals("personType", xmlValError.getBadSchemaType().getName().getLocalPart());
        // todo debug this Assert.assertEquals(xmlValError.getOffendingQName().getLocalPart(), "age");
        assertEquals("Expected element 'name' before the end of the content in element person", xmlValError.getMessage());
    }

    @Test
    void testValidationElementNotAllowedError() throws XmlException {

        String empSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'   elementFormDefault='qualified'>\n" +
            "<xs:element name='age'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:integer'>\n" +
            "<xs:minInclusive value='0'/>\n" +
            "<xs:maxInclusive value='100'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:element>\n" +
            "<xs:element name='empRecords'>\n" +
            "<xs:complexType>\n" +
            "<xs:sequence>\n" +
            "<xs:element name='person' type='personType' maxOccurs='unbounded'/>\n" +
            "</xs:sequence>\n" +
            "</xs:complexType>\n" +
            "</xs:element>\n" +
            "<xs:element name='name' type='xs:string'/>\n" +
            "<xs:complexType name='personType'>\n" +
            "<xs:sequence>\n" +
            "<xs:element ref='name'/>\n" +
            "<xs:element ref='age'/>\n" +
            "</xs:sequence>\n" +
            "<xs:attribute name='employee' use='required'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:NMTOKEN'>\n" +
            "<xs:enumeration value='current'/>\n" +
            "<xs:enumeration value='past'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:attribute>\n" +
            "</xs:complexType>\n" +
            "</xs:schema>\n";
        String[] schemas = {empSchema};

        String xmlInstance =
            "<empRecords xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' >" +
            "<person employee='past'>" +
            "<name>joe blow</name>" +
            "<age>31</age>" +
            "</person>" +
            "<person employee='current'>" +
            "<name>test user</name>" +
            "<age>29</age>" +
            "</person>" +
            "</empRecords>";
        List<XmlError> errors = performValidation(schemas, xmlInstance);
        assertNotNull(errors);
        // todo: enable this assert Assert.assertTrue(errors.size()>0);

        for (Object error : errors) {
            XmlValidationError xmlValError = (XmlValidationError) error;

            assertEquals(xmlValError.getErrorType(), XmlValidationError.ELEMENT_NOT_ALLOWED);
            assertEquals(xmlValError.getBadSchemaType().getName().getLocalPart(), "personType");
            assertEquals(xmlValError.getMessage(), "Expected element(s)");
        }
    }

    @Test
    void testValidationAttributeTypeError() throws XmlException {

        String empSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'   elementFormDefault='qualified'>\n" +
            "<xs:element name='age'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:integer'>\n" +
            "<xs:minInclusive value='0'/>\n" +
            "<xs:maxInclusive value='100'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:element>\n" +
            "<xs:element name='empRecords'>\n" +
            "<xs:complexType>\n" +
            "<xs:sequence>\n" +
            "<xs:element name='person' type='personType' maxOccurs='unbounded'/>\n" +
            "</xs:sequence>\n" +
            "</xs:complexType>\n" +
            "</xs:element>\n" +
            "<xs:element name='name' type='xs:string'/>\n" +
            "<xs:complexType name='personType'>\n" +
            "<xs:sequence>\n" +
            "<xs:element ref='name'/>\n" +
            "<xs:element ref='age'/>\n" +
            "</xs:sequence>\n" +
            "<xs:attribute name='employee' use='required'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:NMTOKEN'>\n" +
            "<xs:enumeration value='current'/>\n" +
            "<xs:enumeration value='past'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:attribute>\n" +
            "</xs:complexType>\n" +
            "</xs:schema>\n";
        String[] schemas = {empSchema};

        String xmlInstance = "<empRecords xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' >" +
                             "<person employee='past'>" +
                             "<name>joe blow</name>" +
                             "<age>31</age>" +
                             "</person>" +
                             "<person employee='current'>" +
                             "<name>test user</name>" +
                             "<age>junk</age>" +
                             "</person>" +
                             "</empRecords>";
        List<XmlError> errors = performValidation(schemas, xmlInstance);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        for (Object error : errors) {
            XmlValidationError xmlValError = (XmlValidationError) error;
            assertEquals(xmlValError.getErrorType(), XmlValidationError.ATTRIBUTE_TYPE_INVALID);
            assertEquals(xmlValError.getMessage(), "Invalid decimal value: unexpected char '106'");
        }
    }

    @Test
    void testElementError() throws XmlException {
        String bobSchema =
            "<xs:schema\n" +
            "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "   xmlns:bob='http://openuri.org/bobschema'\n" +
            "   targetNamespace='http://openuri.org/bobschema'\n" +
            "   elementFormDefault='qualified'>\n" +
            "\n" +
            "  <xs:complexType name='biff'>\n" +
            "   <xs:complexContent>\n" +
            "    <xs:extension base='bob:foo'>\n" +
            "     <xs:sequence>\n" +
            "       <xs:element name='a' minOccurs='0' maxOccurs='unbounded'/>\n" +
            "     </xs:sequence>\n" +
            "    </xs:extension>\n" +
            "   </xs:complexContent>\n" +
            "  </xs:complexType>\n" + "" +
            "  <xs:complexType name='foo'>\n" +
            "  </xs:complexType>\n" + "" +
            "  <xs:element name='foo' type='bob:foo'>\n" +
            "  </xs:element>\n" + "" +
            "</xs:schema>\n";

        String invalid = "<bob:foo xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
                         "xmlns:bob='http://openuri.org/bobschema' " +
                         "xsi:type='bob:biff'><bob:q/></bob:foo>";

        String[] schemas = {bobSchema};

        List<XmlError> errors = performValidation(schemas, invalid);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        for (XmlError error : errors) {
            assertEquals(error.getMessage(), "Expected element 'a@http://openuri.org/bobschema' instead of 'q@http://openuri.org/bobschema' here in element foo@http://openuri.org/bobschema");
            // todo check XmlValidationError
        }
    }

    @Test
    void testAttributeError() throws XmlException {

        String empSchema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'   elementFormDefault='qualified'>\n" +
            "<xs:element name='age'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:integer'>\n" +
            "<xs:minInclusive value='0'/>\n" +
            "<xs:maxInclusive value='100'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:element>\n" +
            "<xs:element name='empRecords'>\n" +
            "<xs:complexType>\n" +
            "<xs:sequence>\n" +
            "<xs:element name='person' type='personType' maxOccurs='unbounded'/>\n" +
            "</xs:sequence>\n" +
            "</xs:complexType>\n" +
            "</xs:element>\n" +
            "<xs:element name='name' type='xs:string'/>\n" +
            "<xs:complexType name='personType'>\n" +
            "<xs:sequence>\n" +
            "<xs:element ref='name'/>\n" +
            "<xs:element ref='age'/>\n" +
            "</xs:sequence>\n" +
            "<xs:attribute name='employee' use='required'>\n" +
            "<xs:simpleType>\n" +
            "<xs:restriction base='xs:NMTOKEN'>\n" +
            "<xs:enumeration value='current'/>\n" +
            "<xs:enumeration value='past'/>\n" +
            "</xs:restriction>\n" +
            "</xs:simpleType>\n" +
            "</xs:attribute>\n" +
            "</xs:complexType>\n" +
            "</xs:schema>\n";
        String[] schemas = {empSchema};

        String xmlInstance =
            "<empRecords xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' >" +
            "<person employee='past'>" +
            "<name>joe blow</name>" +
            "<age>31</age>" +
            "</person>" +
            "<person>" +
            "<name>test user</name>" +
            "<age>29</age>" +
            "</person>" +
            "</empRecords>";
        List<XmlError> errors = performValidation(schemas, xmlInstance);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        for (XmlError error : errors) {
            assertEquals(error.getMessage(), "Expected attribute: employee in element person");
            // todo check XmlValidationError
        }
    }

    @Test
    void testValidate0() throws Exception {
        //
        // The most basic schema
        //

        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "</xs:schema>";

        String[] schemas = {schema};

        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        //
        // One which uses ##targetNamespace on a wildcard
        //

        schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "  <xs:complexType name='foo'>\n" +
            "    <xs:sequence>\n" +
            "      <xs:any namespace='##targetNamespace'/>\n" +
            "    </xs:sequence>\n" +
            "  </xs:complexType>\n" +
            "</xs:schema>";

        String[] schemas99 = {schema};

        stl = makeSchemaTypeLoader(schemas99);

        //
        // A big, nasty schema :-)
        //

        File schemeFile = JarUtil.getResourceFromJarasFile("xbean/xmlobject/store/XMLSchema.xsd");
        File xmlFile = JarUtil.getResourceFromJarasFile("xbean/xmlobject/store/XML.xsd");

        File[] schemasF = {schemeFile, xmlFile};

        stl = makeSchemaTypeLoader(schemasF);

        SchemaType type = stl.findDocumentType(
                new QName("http://www.w3.org/2001/XMLSchema", "schema"));

        assertNotNull(type);


        //
        // A good piece from a J2EE schema
        //

        schema =
            "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xsd:schema xmlns='http://www.w3.org/2001/XMLSchema'\n" +
            "     xmlns:xsd='http://www.w3.org/2001/XMLSchema'\n" +
            "     elementFormDefault='qualified'\n" +
            "     attributeFormDefault='unqualified'>\n" +
            "<xsd:annotation>\n" +
            "<xsd:documentation>\n" +
            "@(#)application-client_1_4.xsds	1.7 07/08/02\n" +
            "</xsd:documentation>\n" +
            "</xsd:annotation>\n" +
            "</xsd:schema>\n";

        String[] schemas5 = {schema};

        stl = makeSchemaTypeLoader(schemas5);

        //
        // A bad schema
        //

        schema = "<foo/>";

        String[] schemas2 = {schema};
        assertThrows(Exception.class, () -> makeSchemaTypeLoader(schemas2));

        //
        // A bad schema
        //
        schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "  <foo/>\n" +
            "</xs:schema>";

        String[] schemas3 = {schema};
        assertThrows(Exception.class, () -> makeSchemaTypeLoader(schemas3));
    }

    @Test
    void testValidate1() throws Exception {
        String ericSchema =
            "<xs:schema\n" +
            "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "   xmlns:nw='http://openuri.org/ericschema'\n" +
            "   targetNamespace='http://openuri.org/ericschema'\n" +
            "   elementFormDefault='qualified'>\n" +
            "\n" +
            "  <xs:complexType name='foo'>\n" +
            "  </xs:complexType>\n" +
            "\n" +
            "  <xs:element name='foo' type='nw:foo'>\n" +
            "  </xs:element>\n" +
            "\n" +
            "  <xs:element name='eric'>\n" +
            "    <xs:complexType>\n" +
            "      <xs:sequence>\n" +
            "        <xs:element name='a' maxOccurs='unbounded'/>\n" +
            "        <xs:element name='b' />\n" +
            "        <xs:any namespace='yaya' minOccurs='0' maxOccurs='1' processContents='lax'/>\n" +
            "        <xs:element name='c' />\n" +
            "        <xs:any minOccurs='0' maxOccurs='unbounded' processContents='strict'/>\n" +
            "      </xs:sequence>\n" +
            "      <xs:attribute name='x' use='optional'/>\n" +
            "      <xs:attribute name='y' use='required'/>\n" +
            "      <xs:attribute name='z' use='prohibited'/>\n" +
            "    </xs:complexType>\n" +
            "  </xs:element>\n" +
            "" +
            "</xs:schema>\n";

        String eric2Schema =
            "<xs:schema\n" +
            "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "   xmlns:nw='http://openuri.org/ericschema2'\n" +
            "   xmlns:eric='http://openuri.org/ericschema'\n" +
            "   targetNamespace='http://openuri.org/ericschema2'\n" +
            "   elementFormDefault='qualified'>\n" +
            "\n" +
            "  <xs:complexType name='eric2'>\n" +
            "    <xs:complexContent>\n" +
            "      <xs:extension base='eric:foo'>\n" +
            "        <xs:sequence>\n" +
            "          <xs:element name='a' maxOccurs='unbounded'/>\n" +
            "        </xs:sequence>\n" +
            "      </xs:extension>\n" +
            "    </xs:complexContent>\n" +
            "  </xs:complexType>\n" +
            "</xs:schema>\n";

        String eric = "<eric y='Y' xmlns='http://openuri.org/ericschema'>";
        String eric2 = "<eric xmlns='http://openuri.org/ericschema2'>";
        String xsi = "xmlns:eric2='http://openuri.org/ericschema2' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'";

        String[] valid = {
            eric + "<a/><b/><c/><foo xsi:type='eric2:eric2' " +
            xsi + "><a xmlns=\"http://openuri.org/ericschema2\"/></foo></eric>",
            eric + "<a/><b/><boo xmlns='yaya'/><c/></eric>",
            eric + "<a/><b/><c/></eric>",
            eric + "<a x='y'/><b/><c/></eric>",
            "<eric y='Y' x='X' xmlns='http://openuri.org/ericschema'>" +
            "<a/><b/><c/></eric>"
        };

        String ericSansY =
            "<eric xmlns='http://openuri.org/ericschema'>";

        String[] invalid = {
            "<foo/>",
            "<eric><a/><foo/><c/></eric>",
            eric + "text<a/><b/><c/></eric>",
            eric + "<a/>text<b/><c/></eric>",
            eric + "<a/><b/>text<c/></eric>",
            eric + "<a/><b/><c/>text</eric>",
            eric + "<a x='y'/><b/><c/>text</eric>",
            eric + "<a/><b/><boo xmlns='yaya'/><moo xmlns='yaya'/><c/></eric>",
            ericSansY + "<a/><b/><c/></eric>",
            "<eric y='' z='' xmlns='http://openuri.org/ericschema'>" +
            "<a/><b/><c/></eric>"
        };

        String[] schemas = {ericSchema, eric2Schema};

        doTest(
            schemas,
            new QName("http://openuri.org/ericschema", "eric"),
            valid, invalid);
    }

    @Test
    void testValidate2() throws Exception {
        String bobSchema =
            "<xs:schema\n" +
            "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "   xmlns:bob='http://openuri.org/bobschema'\n" +
            "   targetNamespace='http://openuri.org/bobschema'\n" +
            "   elementFormDefault='qualified'>\n" +
            "\n" +
            "  <xs:complexType name='biff'>\n" +
            "   <xs:complexContent>\n" +
            "    <xs:extension base='bob:foo'>\n" +
            "     <xs:sequence>\n" +
            "       <xs:element name='a' minOccurs='0' maxOccurs='unbounded'/>\n" +
            "     </xs:sequence>\n" +
            "    </xs:extension>\n" +
            "   </xs:complexContent>\n" +
            "  </xs:complexType>\n" +
            "" +
            "  <xs:complexType name='foo'>\n" +
            "  </xs:complexType>\n" +
            "" +
            "  <xs:element name='foo' type='bob:foo'>\n" +
            "  </xs:element>\n" +
            "" +
            "</xs:schema>\n";

        String[] valid = {
            "<bob:foo xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:bob='http://openuri.org/bobschema' " +
            "xsi:type='bob:biff'><bob:a/><bob:a/><bob:a/></bob:foo>"
        };

        String[] invalid = {
            "<bob:foo xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:bob='http://openuri.org/bobschema' " +
            "xsi:type='bob:biff'><bob:q/></bob:foo>",
            "<bob:foo a='b' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:bob='http://openuri.org/bobschema' " +
            "xsi:type='bob:biff'><bob:a/><bob:a/><bob:a/></bob:foo>"
        };

        String[] schemas = {bobSchema};

        doTest(schemas, null, valid, invalid);
    }

    @Test
    void testValidate3() throws Exception {
        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "" +
            " <xs:simpleType name='allNNI'>\n" +
            "  <xs:annotation><xs:documentation>\n" +
            "   for maxOccurs</xs:documentation></xs:annotation>\n" +
            "  <xs:union memberTypes='xs:nonNegativeInteger'>\n" +
            "   <xs:simpleType>\n" +
            "    <xs:restriction base='xs:NMTOKEN'>\n" +
            "     <xs:enumeration value='unbounded'/>\n" +
            "    </xs:restriction>\n" +
            "   </xs:simpleType>\n" +
            "  </xs:union>\n" +
            " </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='ericBase64Binary'>\n" +
            "    <xs:restriction base='xs:base64Binary'>\n" +
            "      <xs:enumeration value='Eric'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='abcdHexBinary'>\n" +
            "    <xs:restriction base='xs:hexBinary'>\n" +
            "      <xs:enumeration value='abcd'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='zeroNumber'>\n" +
            "    <xs:restriction base='number'>\n" +
            "      <xs:enumeration value='Zero'/>\n" +
            "      <xs:enumeration value='0'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='number'>\n" +
            "    <xs:union>\n" +
            "      <xs:simpleType>\n" +
            "        <xs:restriction base='xs:decimal'/>\n" +
            "      </xs:simpleType>\n" +
            "      <xs:simpleType>\n" +
            "        <xs:restriction base='xs:string'>\n" +
            "          <xs:whiteSpace value='collapse'/>\n" +
            "          <xs:enumeration value='Zero'/>\n" +
            "          <xs:enumeration value='One'/>\n" +
            "          <xs:enumeration value='Two'/>\n" +
            "          <xs:enumeration value='Three'/>\n" +
            "          <xs:enumeration value='Many'/>\n" +
            "        </xs:restriction>\n" +
            "      </xs:simpleType>\n" +
            "    </xs:union>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='listOfInt'>\n" +
            "    <xs:list itemType='xs:int'/>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='listOfPrime'>\n" +
            "    <xs:list itemType='prime'/>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='ericBrother'>\n" +
            "    <xs:restriction base='xs:string'>\n" +
            "      <xs:whiteSpace value='collapse'/>\n" +
            "      <xs:enumeration value='Brian'/>\n" +
            "      <xs:enumeration value='Kevin'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='ericBrothers'>\n" +
            "    <xs:list itemType='ericBrother'/>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='weekString'>\n" +
            "    <xs:restriction base='xs:string'>\n" +
            "      <xs:whiteSpace value='collapse'/>\n" +
            "      <xs:enumeration value='Monday'/>\n" +
            "      <xs:enumeration value='Tuesday'/>\n" +
            "      <xs:enumeration value='Wednesday'/>\n" +
            "      <xs:enumeration value='Thursday'/>\n" +
            "      <xs:enumeration value='Friday'/>\n" +
            "      <xs:enumeration value='Saturday'/>\n" +
            "      <xs:enumeration value='Sunday'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='aYear'>\n" +
            "    <xs:restriction base='xs:duration'>\n" +
            "      <xs:enumeration value='P1Y'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='ericBDay'>\n" +
            "    <xs:restriction base='xs:date'>\n" +
            "      <xs:enumeration value='1965-06-10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='youngerThanEric'>\n" +
            "    <xs:restriction base='xs:date'>\n" +
            "      <xs:minExclusive value='1965-06-10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='fiveCharQname'>\n" +
            "    <xs:restriction base='xs:QName'>\n" +
            "      <xs:length value='5'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='wackyQname'>\n" +
            "    <xs:restriction base='xs:QName'>\n" +
            "      <xs:minLength value='3'/>\n" +
            "      <xs:maxLength value='10'/>\n" +
            "      <xs:pattern value='[xs:abcde]*'/>\n" +
            "      <xs:enumeration value='xs:abc'/>\n" +
            "      <xs:enumeration value='xs:bcd'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='fiveCharAnyURI'>\n" +
            "    <xs:restriction base='xs:anyURI'>\n" +
            "      <xs:length value='5'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='wackyAnyURI'>\n" +
            "    <xs:restriction base='xs:anyURI'>\n" +
            "      <xs:minLength value='3'/>\n" +
            "      <xs:maxLength value='10'/>\n" +
            "      <xs:enumeration value='foo'/>\n" +
            "      <xs:enumeration value='bar'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='bit'>\n" +
            "    <xs:restriction base='xs:boolean'>\n" +
            "      <xs:pattern value='1|0'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='evenInteger'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:pattern value='[0-9]*[02468]'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='prime'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:pattern value='[0-9]*[13579]'/>\n" +
            "      <xs:enumeration value='3'/>\n" +
            "      <xs:enumeration value='5'/>\n" +
            "      <xs:enumeration value='7'/>\n" +
            "      <xs:enumeration value='11'/>\n" +
            "      <xs:enumeration value='13'/>\n" +
            "      <xs:enumeration value='17'/>\n" +
            "      <xs:enumeration value='19'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='fourCharString'>\n" +
            "    <xs:restriction base='xs:string'>\n" +
            "      <xs:length value='4'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='threeOrMoreCharString'>\n" +
            "    <xs:restriction base='xs:string'>\n" +
            "      <xs:minLength value='3'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='fiveOrLessCharString'>\n" +
            "    <xs:restriction base='xs:string'>\n" +
            "      <xs:maxLength value='5'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='fiveTotalDigits'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:totalDigits value='5'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='threeFractionDigits'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:fractionDigits value='3'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMinExclusive'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:minExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxExclusive'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:maxExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxInclusive'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:maxInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMinInclusive'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:minInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMinExclusiveFloat'>\n" +
            "    <xs:restriction base='xs:float'>\n" +
            "      <xs:minExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxExclusiveFloat'>\n" +
            "    <xs:restriction base='xs:float'>\n" +
            "      <xs:maxExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxInclusiveFloat'>\n" +
            "    <xs:restriction base='xs:float'>\n" +
            "      <xs:maxInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMinInclusiveFloat'>\n" +
            "    <xs:restriction base='xs:float'>\n" +
            "      <xs:minInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "\n" +
            "  <xs:simpleType name='tenMinExclusiveDouble'>\n" +
            "    <xs:restriction base='xs:double'>\n" +
            "      <xs:minExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxExclusiveDouble'>\n" +
            "    <xs:restriction base='xs:double'>\n" +
            "      <xs:maxExclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMaxInclusiveDouble'>\n" +
            "    <xs:restriction base='xs:double'>\n" +
            "      <xs:maxInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "" +
            "  <xs:simpleType name='tenMinInclusiveDouble'>\n" +
            "    <xs:restriction base='xs:double'>\n" +
            "      <xs:minInclusive value='10'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "\n" +
            "  <xs:element name='any'>\n" +
            "  </xs:element>\n" +
            "" +
            "  <xs:element name='default_12345' default='12345'>\n" +
            "  </xs:element>\n" +
            "" +
            "  <xs:element name='default_1234' default='1234'>\n" +
            "  </xs:element>\n" +
            "" +
            "  <xs:element name='default_eric' default='eric'>\n" +
            "  </xs:element>\n" +
            "" +
            "</xs:schema>\n";

        String ns = "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
                    "xmlns:xs='http://www.w3.org/2001/XMLSchema'";
        String[] valid = {
            "<any " + ns + " xsi:type='threeFractionDigits'>.1</any>",
            "<any " + ns + " xsi:type='threeFractionDigits'>.12</any>",
            "<any " + ns + " xsi:type='threeFractionDigits'>.123</any>",

            "<any " + ns + " xsi:type='allNNI'>unbounded</any>",
            "<any " + ns + " xsi:type='allNNI'>0</any>",
            "<any " + ns + " xsi:type='allNNI'>1</any>",

            "<any " + ns + " xsi:type='xs:base64Binary'>CAFEBABE</any>",
            "<any " + ns + " xsi:type='abcdHexBinary'>abcd</any>",

            "<any " + ns + " xsi:type='xs:base64Binary'>abcdefgh</any>",
            "<any " + ns + " xsi:type='ericBase64Binary'>Eric</any>",

            "<any " + ns + " xsi:type='zeroNumber'> Zero</any>",
            "<any " + ns + " xsi:type='zeroNumber'>Zero</any>",
            "<any " + ns + " xsi:type='zeroNumber'>0</any>",

            "<any " + ns + " xsi:type='number'>1</any>",
            "<any " + ns + " xsi:type='number'> 1 </any>",
            "<any " + ns + " xsi:type='number'>Two</any>",
            "<any " + ns + " xsi:type='number'> Three </any>",

            "<any " + ns + " xsi:type='ericBrothers'></any>",
            "<any " + ns + " xsi:type='ericBrothers'>Brian</any>",
            "<any " + ns + " xsi:type='ericBrothers'>Kevin</any>",
            "<any " + ns + " xsi:type='ericBrothers'>Kevin Brian</any>",
            "<any " + ns + " xsi:type='ericBrothers'>Brian Kevin</any>",

            "<any " + ns + " xsi:type='listOfInt'>  1  22  333  4444  </any>",
            "<any " + ns + " xsi:type='listOfInt'></any>",
            "<any " + ns + " xsi:type='listOfInt'>5999</any>",

            "<any " + ns + " xsi:type='aYear'>P1Y</any>",

            "<any " + ns + " xsi:type='fiveCharAnyURI'>abcde</any>",
            "<any " + ns + " xsi:type='fiveCharQname'>xs:abc</any>",
            "<any " + ns + " xsi:type='xs:anyURI'>foo</any>",
            "<any " + ns + " xsi:type='wackyAnyURI'>foo</any>",

            "<any " + ns + " xsi:type='youngerThanEric'>1965-06-11</any>",
            "<any " + ns + " xsi:type='ericBDay'>1965-06-10</any>",
            "<any " + ns + " xsi:type='xs:dateTime'>1999-05-31T13:20:00-05:00</any>",
            "<any " + ns + " xsi:type='xs:time'>00:00:00</any>",
            "<any " + ns + " xsi:type='xs:time'>13:20:00-05:00</any>",

            "<any " + ns + " xsi:type='wackyQname'>xs:abc</any>",
            "<any " + ns + " xsi:type='fiveCharQname'>abcde</any>",
            "<any " + ns + " xsi:type='fiveCharQname'>xs:ab</any>",
            "<any " + ns + " xsi:type='prime'>3</any>",
            "<any " + ns + " xsi:type='prime'>11</any>",
            "<any " + ns + " xsi:type='xs:integer'>+45</any>",
            "<any " + ns + " xsi:type='xs:integer'>1</any>",
            "<any " + ns + " xsi:type='xs:integer'>0</any>",
            "<any " + ns + " xsi:type='xs:integer'>-1</any>",
            "<any " + ns + " xsi:type='xs:integer'>-1</any>",
            "<any " + ns + " xsi:type='xs:integer'>489743579837589743434</any>",
            "<any " + ns + " xsi:type='xs:boolean'>1</any>",
            "<default_1234 " + ns + " xsi:type='evenInteger'></default_1234>",
            "<any " + ns + " xsi:type='evenInteger'>12</any>",
            "<any " + ns + " xsi:type='bit'>1</any>",
            "<any " + ns + " xsi:type='bit'>0</any>",
            "<any " + ns + " xsi:type='xs:boolean'>false</any>",
            "<any " + ns + " xsi:type='evenInteger'>0</any>",
            "<any " + ns + " xsi:type='weekString'>Monday</any>",
            "<any " + ns + " xsi:type='weekString'>Sunday</any>",
            "<any " + ns + " xsi:type='weekString'>  Thursday  </any>",

            "<any " + ns + " xsi:type='tenMinExclusive'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusive'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusive'>10</any>",
            "<any " + ns + " xsi:type='tenMinInclusive'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxInclusive'>10</any>",
            "<any " + ns + " xsi:type='tenMaxInclusive'>9.9</any>",

            "<any " + ns + " xsi:type='tenMinExclusiveFloat'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusiveFloat'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveFloat'>10</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveFloat'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveFloat'>10</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveFloat'>9.9</any>",

            "<any " + ns + " xsi:type='tenMinExclusiveDouble'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusiveDouble'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveDouble'>10</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveDouble'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveDouble'>10</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveDouble'>9.9</any>",

            "<any " + ns + " xsi:type='fourCharString'>eric</any>",
            "<any " + ns + " xsi:type='threeOrMoreCharString'>12345</any>",
            "<any " + ns + " xsi:type='fiveOrLessCharString'>1234</any>",
            "<any " + ns + " xsi:type='fiveTotalDigits'>12345</any>",
            "<any " + ns + " xsi:type='fiveTotalDigits'>1234</any>",
            "<default_1234 " + ns + " xsi:type='evenInteger'>\n\n</default_1234>"
        };

        String[] invalid = {
            "<any " + ns + " xsi:type='allNNI'>foo</any>",
            "<any " + ns + " xsi:type='xs:hexBinary'>P</any>",
            "<any " + ns + " xsi:type='xs:hexBinary'>CAFEBABP</any>",
            "<any " + ns + " xsi:type='abcdHexBinary'>abce</any>",

            "<any " + ns + " xsi:type='xs:base64Binary'>abcde</any>",
            "<any " + ns + " xsi:type='ericBase64Binary'>Erik</any>",

            "<any " + ns + " xsi:type='zeroNumber'>One</any>",
            "<any " + ns + " xsi:type='zeroNumber'>Twenty</any>",

            "<any " + ns + " xsi:type='number'>Seven</any>",
            "<any " + ns + " xsi:type='number'>Bob</any>",
            "<any " + ns + " xsi:type='number'></any>",

            "<any " + ns + " xsi:type='ericBrothers'>1</any>",
            "<any " + ns + " xsi:type='ericBrothers'>Bob</any>",
            "<any " + ns + " xsi:type='ericBrothers'>Ralph Frank</any>",

            "<any " + ns + " xsi:type='listOfInt'>  1  22  333  Eric  </any>",
            "<any " + ns + " xsi:type='listOfInt'>Eric</any>",
            "<any " + ns + " xsi:type='listOfInt'>-</any>",

            "<any " + ns + " xsi:type='aYear'>P2Y</any>",

            "<any " + ns + " xsi:type='youngerThanEric'>1965-06-10</any>",
            "<any " + ns + " xsi:type='ericBDay'>1985-06-10</any>",
            "<any " + ns + " xsi:type='xs:dateTime'>xx1999-05-31T13:20:00-05:00</any>",
            "<any " + ns + " xsi:type='xs:dateTime'>eric</any>",
            "<any " + ns + " xsi:type='xs:time'>99:99:00</any>",
            "<any " + ns + " xsi:type='xs:time'>13:20:00-99:00</any>",

            "<any " + ns + " xsi:type='wackyAnyURI'>moo</any>",
            "<any " + ns + " xsi:type='fiveCharAnyURI'>ab</any>",

            "<any " + ns + " xsi:type='wackyQname'>xs:abcdefghijk</any>",
            "<any " + ns + " xsi:type='wackyQname'>xs:pqr</any>",
            "<any " + ns + " xsi:type='xs:QName'>foo:bar</any>",
            "<any " + ns + " xsi:type='prime'>12</any>",
            "<any " + ns + " xsi:type='prime'>6</any>",
            "<any " + ns + " xsi:type='xs:integer'>foo</any>",
            "<any " + ns + " xsi:type='xs:integer'>.1</any>",
            "<any " + ns + " xsi:type='evenInteger'>1</any>",
            "<any " + ns + " xsi:type='bit'>true</any>",
            "<any " + ns + " xsi:type='bit'>false</any>",
            "<any " + ns + " xsi:type='bit'>nibble</any>",
            "<any " + ns + " xsi:type='bit'>2</any>",
            "<any " + ns + " xsi:type='xs:boolean'>blurf</any>",
            "<any " + ns + " xsi:type='xs:boolean'></any>",
            "<any " + ns + " xsi:type='evenInteger'></any>",
            "<any " + ns + " xsi:type='weekString'>Monday Sucks</any>",

            "<any " + ns + " xsi:type='tenMaxExclusive'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusive'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusive'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusive'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusive'>9.9</any>",
            "<any " + ns + " xsi:type='tenMaxInclusive'>10.1</any>",

            "<any " + ns + " xsi:type='tenMaxExclusiveFloat'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusiveFloat'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusiveFloat'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusiveFloat'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveFloat'>9.9</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveFloat'>10.1</any>",

            "<any " + ns + " xsi:type='tenMaxExclusiveDouble'>10.1</any>",
            "<any " + ns + " xsi:type='tenMaxExclusiveDouble'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusiveDouble'>10</any>",
            "<any " + ns + " xsi:type='tenMinExclusiveDouble'>9.9</any>",
            "<any " + ns + " xsi:type='tenMinInclusiveDouble'>9.9</any>",
            "<any " + ns + " xsi:type='tenMaxInclusiveDouble'>10.1</any>",

            "<any " + ns + " xsi:type='fiveOrLessCharString'> 1234 </any>",
            "<any " + ns + " xsi:type='fiveTotalDigits'>123456</any>",
            "<any " + ns + " xsi:type='fourCharString'>vasilik</any>",
            "<any " + ns + " xsi:type='threeOrMoreCharString'>1</any>",
            "<any " + ns + " xsi:type='fiveOrLessCharString'>1234567</any>",
            "<any " + ns + " xsi:type='threeFractionDigits'>.1234</any>",
            "<any " + ns + " xsi:type='fourCharString'> eric </any>",

            "<default_12345 " + ns + " xsi:type='evenInteger'></default_12345>",
            "<default_12345 " + ns + " xsi:type='evenInteger'>\n\n</default_12345>"
        };

        String[] schemas = {schema};

        doTest(schemas, null, valid, invalid);
    }


    @Test
    void testValidate5() throws Exception {
        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "\n" +
            "  <xs:element name='hee'>\n" +
            "    <xs:complexType>\n" +
            "      <xs:sequence>\n" +
            "        <xs:element name='haw' type='xs:int'/>\n" +
            "      </xs:sequence>\n" +
            "      <xs:attribute name='yee' type='xs:int'/>\n" +
            "    </xs:complexType>\n" +
            "  </xs:element>\n" +
            "\n" +
            "  <xs:simpleType name='kindaPrime'>\n" +
            "    <xs:restriction base='xs:decimal'>\n" +
            "      <xs:pattern value='[0-9]*[13579]'/>\n" +
            "      <xs:enumeration value='3'/>\n" +
            "      <xs:enumeration value='5'/>\n" +
            "      <xs:enumeration value='7'/>\n" +
            "      <xs:enumeration value='11'/>\n" +
            "      <xs:enumeration value='13'/>\n" +
            "      <xs:enumeration value='17'/>\n" +
            "      <xs:enumeration value='19'/>\n" +
            "    </xs:restriction>\n" +
            "  </xs:simpleType>\n" +
            "\n" +
            "</xs:schema>\n" +
            "";

        String[] schemas = {schema};

        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        XmlObject x =
            stl.parse(
                "<hee yee='3'><haw>66</haw></hee>",
                null, null);

        try (XmlCursor c = x.newCursor()) {
            do {
                XmlObject obj = c.getObject();

                if (obj != null) {
                    obj.validate();
                }

            } while (!c.toNextToken().isNone());
        }

        // invalid

        x =
            stl.parse(
                "<hee yee='five'><haw>66</haw></hee>",
                null, null);

        assertFalse(x.validate());

        try (XmlCursor c = x.newCursor()) {
            c.toNextToken();
            c.toNextToken();

            assertFalse(c.getObject().validate());
        }

        // No schema

        x = XmlObject.Factory.parse("<foo x='y'>asas<bar>asas</bar></foo>");

        try (XmlCursor c = x.newCursor()) {
            do {
                XmlObject obj = c.getObject();

                if (obj != null) {
                    obj.validate();
                }

            } while (!c.toNextToken().isNone());
        }
    }

    @Test
    void testValidate6() throws Exception {
        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "  <xs:element name='hee'>\n" +
            "  </xs:element>\n" +
            "</xs:schema>\n" +
            "";

        String[] schemas = {schema, schema};


        // Should get a schema compile error
        assertThrows(XmlException.class, () -> makeSchemaTypeLoader(schemas));
    }

    @Test
    void testValidate7() throws Exception {
        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "" +
            "  <xs:element name='base' type='base'/>" +
            "" +
            "  <xs:complexType name='base'>" +
            "    <xs:sequence>" +
            "      <xs:element name='foo'/>" +
            "    </xs:sequence>" +
            "  </xs:complexType>" +
            "" +
            "  <xs:complexType name='derived'>" +
            "    <xs:complexContent>" +
            "      <xs:extension base='base'>" +
            "        <xs:sequence>" +
            "          <xs:element name='bar'/>" +
            "        </xs:sequence>" +
            "      </xs:extension>" +
            "    </xs:complexContent>" +
            "  </xs:complexType>" +
            "" +
            "</xs:schema>" +
            "";

        String[] schemas = {schema};

        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        XmlObject x =
            stl.parse(
                "<base><foo/></base>", null, null);

        assertTrue(x.validate());

        try (XmlCursor c = x.newCursor()) {
            c.toFirstChild();

            XmlObject base = c.getObject();

            c.toEndToken();
            c.insertElement("bar");

            assertFalse(x.validate());

            c.toPrevSibling();

            c.removeXml();

            assertTrue(x.validate());

            base.changeType(stl.findType(new QName("derived")));

            c.insertElement("bar");

            assertTrue(x.validate());
        }
    }

    // Tests abstract & block attributes on ComplexType
    @Test
    void testValidate8() throws Exception {
        String[] schemas = {
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "" +
            "  <xs:element name='abstract' type='abstract'/>" +
            "  <xs:element name='base' type='base'/>" +
            "" +
            "  <xs:complexType name='abstract' abstract='true'/>" +
            "" +
            "  <xs:complexType name='concrete'>" +
            "    <xs:complexContent>" +
            "      <xs:extension base='abstract'/>" +
            "    </xs:complexContent>" +
            "  </xs:complexType>" +
            "" +
            "  <xs:complexType name='base' block='extension'/>" +
            "" +
            "  <xs:complexType name='ext'>" +
            "    <xs:complexContent>" +
            "      <xs:extension base='base'/>" +
            "    </xs:complexContent>" +
            "  </xs:complexType>" +
            "" +
            "  <xs:complexType name='rest'>" +
            "    <xs:complexContent>" +
            "      <xs:restriction base='base'/>" +
            "    </xs:complexContent>" +
            "  </xs:complexType>" +
            "" +
            "</xs:schema>" +
            "",
        };

        String xsiType = " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' ";

        String[] valid = {
            "<abstract" + xsiType + "xsi:type='concrete'/>",
            "<base/>",
            "<base" + xsiType + "xsi:type='rest'/>",
        };
        String[] invalid = {
            "<abstract/>",
            "<base" + xsiType + "xsi:type='ext'/>",
        };

        doTest(schemas, null, valid, invalid);
    }

    @Test
    void testValidate9() throws Exception {

        String[] schemas = {
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "   <xs:element name='order' type='OrderType'>" +
            "       <xs:keyref name='prodNumKeyRef' refer='prodNumKey'>" +
            "           <xs:selector xpath='items/*'/>" +
            "           <xs:field xpath='@number'/>" +
            "       </xs:keyref>" +
            "       <xs:key name='prodNumKey'>" +
            "           <xs:selector xpath='.//product'/>" +
            "           <xs:field xpath='number'/>" +
            "       </xs:key>" +
            "   </xs:element>" +

            "   <xs:complexType name='OrderType'>" +
            "       <xs:sequence>" +
            "           <xs:element name='items'>" +
            "               <xs:complexType>" +
            "                   <xs:sequence>" +
            "                       <xs:element name='item' type='ItemType' minOccurs='0' maxOccurs='unbounded'/>" +
            "                   </xs:sequence>" +
            "               </xs:complexType>" +
            "           </xs:element>" +
            "           <xs:element name='products'>" +
            "               <xs:complexType>" +
            "                   <xs:sequence>" +
            "                       <xs:element name='product' type='ProductType' maxOccurs='unbounded' minOccurs='0'/>" +
            "                   </xs:sequence>" +
            "               </xs:complexType>" +
            "           </xs:element>" +
            "       </xs:sequence>" +
            "       <xs:attribute name='number' type='xs:string'/>" +
            "   </xs:complexType>" +

            "   <xs:complexType name='ItemType'>" +
            "       <xs:sequence>" +
            "           <xs:element name='quantity' type='xs:int'/>" +
            "       </xs:sequence>" +
            "       <xs:attribute name='number' type='xs:int'/>" +
            "   </xs:complexType>" +

            "   <xs:complexType name='ProductType'>" +
            "       <xs:sequence>" +
            "           <xs:element name='number' type='xs:int' minOccurs='0'/>" +
            "           <xs:element name='name' type='xs:string'/>" +
            "           <xs:element name='price'>" +
            "               <xs:complexType>" +
            "                   <xs:simpleContent>" +
            "                       <xs:extension base='xs:decimal'>" +
            "                           <xs:attribute name='currency' type='xs:string'/>" +
            "                       </xs:extension>" +
            "                   </xs:simpleContent>" +
            "               </xs:complexType>" +
            "           </xs:element>" +
            "       </xs:sequence>" +
            "   </xs:complexType>" +
            "</xs:schema> ",

            "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>" +

            "    <xsd:element name='root' type='RootType'>" +
            "        <xsd:key name='FooId'>" +
            "            <xsd:selector xpath='.//string|.//token|.//int'/>" +
            "            <xsd:field xpath='@id'/>" +
            "        </xsd:key>" +
            "    </xsd:element>" +

            "    <xsd:group name='group'>" +
            "        <xsd:choice>" +
            "            <xsd:element ref='string'/>" +
            "            <xsd:element ref='token'/>" +
            "            <xsd:element ref='int'/>" +
            "        </xsd:choice>" +
            "    </xsd:group>" +

            "    <xsd:complexType name='RootType'>" +
            "        <xsd:group ref='group' minOccurs='0' maxOccurs='unbounded'/>" +
            "    </xsd:complexType>" +

            "    <xsd:element name='string'>" +
            "       <xsd:complexType>" +
            "            <xsd:group ref='group' minOccurs='0' maxOccurs='unbounded'/>" +
            "            <xsd:attribute name='id' type='xsd:string'/>" +
            "        </xsd:complexType>" +
            "    </xsd:element>" +

            "    <xsd:element name='int'>" +
            "        <xsd:complexType>" +
            "            <xsd:group ref='group' minOccurs='0' maxOccurs='unbounded'/>" +
            "            <xsd:attribute name='id' type='xsd:int'/>" +
            "        </xsd:complexType>" +
            "    </xsd:element>" +

            "    <xsd:element name='token'>" +
            "        <xsd:complexType>" +
            "            <xsd:group ref='group' minOccurs='0' maxOccurs='unbounded'/>" +
            "            <xsd:attribute name='id' type='xsd:token'/>" +
            "        </xsd:complexType>" +
            "    </xsd:element>" +

            "</xsd:schema>",

            "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema' " +
            "    targetNamespace='http://www.tim-hanson.com/' xmlns:th='http://www.tim-hanson.com/' " +
            "    attributeFormDefault='qualified' elementFormDefault='qualified'>" +

            "    <xsd:element name='root'>" +
            "        <xsd:complexType>" +
            "            <xsd:sequence>" +
            "                <xsd:element name='foo' maxOccurs='unbounded'>" +
            "                    <xsd:complexType>" +
            "                        <xsd:attribute name='id' type='xsd:int'/>" +
            "                    </xsd:complexType>" +
            "                </xsd:element>" +
            "            </xsd:sequence>" +
            "        </xsd:complexType>" +
            "        <xsd:key name='id'>" +
            "            <xsd:selector xpath='./th:foo'/>" +
            "            <xsd:field xpath='@th:id'/>" +
            "        </xsd:key>" +
            "    </xsd:element>" +
            "</xsd:schema>",

            "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema' >" +
            "    <xsd:element name='idtest'>" +
            "        <xsd:complexType>" +
            "            <xsd:sequence maxOccurs='unbounded'>" +
            "                <xsd:choice>" +
            "                    <xsd:element name='id' maxOccurs='unbounded' type='xsd:ID'/>" +
            "                    <xsd:element name='idref' maxOccurs='unbounded' type='xsd:IDREF'/>" +
            "                    <xsd:element name='idrefs' maxOccurs='unbounded' type='xsd:IDREFS'/>" +
            "                </xsd:choice>" +
            "            </xsd:sequence>" +
            "        </xsd:complexType>" +
            "    </xsd:element>" +
            "</xsd:schema>",

        };

        String[] valid = {
            "<order>" +
            "    <items>" +
            "      <item number='124 '>" +
            "          <quantity>1</quantity>" +
            "      </item>" +
            "      <item number=' 563  '>" +
            "          <quantity>1</quantity>" +
            "      </item>" +
            "    </items>" +
            "    <products>" +
            "        <product>" +
            "            <number> 124</number>" +
            "            <name>Shirt</name>" +
            "            <price currency='USD'>29.99</price>" +
            "        </product>" +
            "        <product>" +
            "            <number> 563 </number>" +
            "            <name>Hat</name>" +
            "            <price currency='USD'>69.99</price>" +
            "        </product>" +
            "        <product>" +
            "            <number>443</number>" +
            "            <name>Umbrella</name>" +
            "            <price currency='USD'>49.99</price>" +
            "        </product>" +
            "    </products>" +
            "</order>",

            "<root>" +
            "    <string id='foo1'>" +
            "        <string id='foo2'>" +
            "            <string id='foo3'>" +
            "                <string id='foo6'>" +
            "                    <string id='foo7'>" +
            "                        <string id='foo8'/>" +
            "                    </string>" +
            "                </string>" +
            "            </string>" +
            "            <string id='foo4'/>" +
            "        </string>" +
            "        <string id='foo9'/>" +
            "    </string>" +
            "</root>",

            "<root>" +
            "    <int id='1'/>" +
            "    <string id='1'/>" +
            "</root>",

            "<xyz:root xmlns:xyz='http://www.tim-hanson.com/'>" +
            "  <xyz:foo xyz:id='1'/>" +
            "  <xyz:foo xyz:id='2'/>" +
            "  <xyz:foo xyz:id='3'/>" +
            "</xyz:root>",

            "<idtest>" +
            "  <idref>xyz</idref>" +
            "  <idrefs>abc def</idrefs>" +
            "  <id>abc</id>" +
            "  <id>def</id>" +
            "  <id>xyz</id>" +
            "  <idref>abc</idref>" +
            "  <idrefs>xyz abc</idrefs>" +
            "</idtest>",
        };

        String[] invalid = {
            "<order>" +
            "    <items>" +
            "      <item number='125 '>" +
            "          <quantity>1</quantity>" +
            "      </item>" +
            "      <item number='563'>" +
            "          <quantity>1</quantity>" +
            "      </item>" +
            "    </items>" +
            "    <products>" +
            "        <product>" +
            "            <number> 124</number>" +
            "            <name>Shirt</name>" +
            "            <price currency='USD'>29.99</price>" +
            "        </product>" +
            "        <product>" +
            "            <number>563</number>" +
            "            <name>Hat</name>" +
            "            <price currency='USD'>69.99</price>" +
            "        </product>" +
            "        <product>" +
            "            <number>443</number>" +
            "            <name>Umbrella</name>" +
            "            <price currency='USD'>49.99</price>" +
            "        </product>" +
            "    </products>" +
            "</order>",

            "<root>" +
            "    <token token='  blah  blah'/>" +
            "    <string id='blah blah'/>" +
            "</root>",

            "<root>" +
            "    <string id='foo1'>" +
            "        <string id='foo2'>" +
            "            <string id='foo3'>" +
            "                <string id='foo6'>" +
            "                    <string id='foo7'>" +
            "                        <string id='foo3'/>" +
            "                    </string>" +
            "                </string>" +
            "            </string>" +
            "        </string>" +
            "    </string>" +
            "</root>",

            "<xyz:root xmlns:xyz='http://www.tim-hanson.com/'>" +
            "  <xyz:foo xyz:id='1'/>" +
            "  <xyz:foo xyz:id='2'/>" +
            "  <xyz:foo xyz:id='2'/>" +
            "</xyz:root>",
        };

        String[] invalidOnDocOnly = {
            "<idtest>" +
            "  <idref>foo</idref>" +
            "  <id>abc</id>" +
            "  <id>def</id>" +
            "  <id>xyz</id>" +
            "</idtest>",

            "<idtest>" +
            "  <idrefs>abc foo</idrefs>" +
            "  <id>abc</id>" +
            "  <id>def</id>" +
            "  <id>xyz</id>" +
            "</idtest>",
        };


        doTest(schemas, null, valid, invalid, true);
        doTest(schemas, null, valid, invalid, false);

        // IDRefs are validated only if starting at the very root of the world
        doTest(schemas, null, new String[0], invalidOnDocOnly, true);
        doTest(schemas, null, invalidOnDocOnly, new String[0], false);
    }

    // Test validation of setting with the ValidateOnSet option
    @Test
    void testValidate10() throws Exception {
        String schema =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
            "  <xs:simpleType name='dec-restriction'>" +
            "    <xs:restriction base='xs:decimal'>" +
            "      <xs:maxExclusive value='100'/>" +
            "    </xs:restriction>" +
            "  </xs:simpleType>" +
            "</xs:schema>";

        String[] schemas = {schema};

        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        XmlOptions validate = new XmlOptions().setValidateOnSet();
        XmlOptions noValidate = new XmlOptions();

        SchemaType st = stl.findType(new QName("", "dec-restriction"));

        XmlDecimal dec1 = (XmlDecimal) stl.newInstance(st, validate);
        assertThrows(XmlValueOutOfRangeException.class, () -> dec1.setStringValue("200"));

        XmlDecimal dec2 = (XmlDecimal) stl.newInstance(st, noValidate);
        dec2.setStringValue("200");
    }

    // tests numeral validation
    @Test
    void testValidate11() throws Exception {
        String schema =
            "<xs:schema\n" +
            "   xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
            "   targetNamespace='http://openuri.org/testNumerals'\n" +
            "   elementFormDefault='qualified'>\n" +
            "\n" +
            "  <xs:element name='doc'>\n" +
            "    <xs:complexType>\n" +
            "      <xs:sequence>\n" +
            "        <xs:choice minOccurs='0' maxOccurs='unbounded'>\n" +
            "          <xs:element name='int' type='xs:int' />\n" +
            "          <xs:element name='short' type='xs:short' />\n" +
            "          <xs:element name='byte' type='xs:byte' />\n" +
            "        </xs:choice>\n" +
            "      </xs:sequence>\n" +
            "    </xs:complexType>\n" +
            "  </xs:element>\n" +
            "" +
            "</xs:schema>\n";

        String[] valid = {
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> \n -10" +
            "  </int>" +
            "</doc>",
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> \n -<!--comment-->9" +
            "  </int>" +
            "</doc>",
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> +0008" +
            "  </int>" +
            "</doc>",
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> +07<!--comment-->0" +
            "  </int>" +
            "</doc>"
        };

        String[] invalid = {
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int />" +
            "<doc>",
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> </int>" +
            "<doc>",
            "<doc xmlns='http://openuri.org/testNumerals'>" +
            "  <int> + 4 </int>" +
            "<doc>"
        };

        String[] schemas = {schema};

        doTest(
            schemas,
            new QName("http://openuri.org/testNumerals", "doc"),
            valid, invalid);
    }

    // Bugzilla bug #26105: validate derived type from base type enumeration
    @Test
    void testValidate12() throws Exception {
        String[] schemas = {
            "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n" +

            "<xsd:element name='enumDef' type='enumDefType'/>\n" +
            "<xsd:complexType name='enumDefType'>\n" +
            "  <xsd:simpleContent>\n" +
            "    <xsd:extension base='enumType'/>\n" +
            "  </xsd:simpleContent>\n" +
            "</xsd:complexType>\n" +

            "<xsd:simpleType name='enumType'>\n" +
            "  <xsd:restriction base='xsd:token'>\n" +
            "    <xsd:enumeration value='enum1'/>\n" +
            "  </xsd:restriction>\n" +
            "</xsd:simpleType>\n" +
            "</xsd:schema>\n",
        };

        String[] valid = {
            "<enumDef>enum1</enumDef>",
        };

        String[] invalid = {
            "<enumDef>enum2/enumDef>",
        };

        doTest(schemas, null, valid, invalid);
    }

    @Test
    void testValidateNestedGroups() throws Exception {
        // This is a weird Schema, inspired from JIRA bug XMLBEANS-35
        // Make sure we compile it and then validate correctly
        String[] schemas = {
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://tempuri.org/f_up_groups\" xmlns:tns=\"http://tempuri.org/f_up_groups\">\n" +
            "\n" +
            "<xs:group name=\"d\">\n" +
            "	<xs:sequence>\n" +
            "	    <xs:element name=\"error\">\n" +
            "	    	<xs:complexType>\n" +
            "	    		<xs:group ref=\"tns:e\"/>\n" +
            "	    	</xs:complexType>\n" +
            "	    </xs:element>\n" +
            "	</xs:sequence>\n" +
            "</xs:group>\n" +
            "\n" +
            "<xs:group name=\"e\">\n" +
            "	<xs:sequence>\n" +
            "		<xs:element name=\"error\" minOccurs=\"0\">\n" +
            "			<xs:complexType>\n" +
            "				<xs:group ref=\"tns:d\"/>\n" +
            "			</xs:complexType>\n" +
            "		</xs:element>\n" +
            "	</xs:sequence>\n" +
            "</xs:group>\n" +
            "\n" +
            "<xs:element name=\"root\">\n" +
            "	<xs:complexType>\n" +
            " 	<xs:group ref=\"tns:d\"/>\n" +
            "	</xs:complexType>\n" +
            "</xs:element>\n" +
            "\n" +
            "</xs:schema>\n"};

        String[] valid = {
            "<ns:root xmlns:ns=\"http://tempuri.org/f_up_groups\">\n" +
            "   <error>\n" +
            "      <error>\n" +
            "         <error/>" +
            "      </error>\n" +
            "    </error>\n" +
            "</ns:root>\n"};

        String[] invalid = {
            "<ns:root xmlns:ns=\"http://tempuri.org/f_up_groups\">\n" +
            "   <error>\n" +
            "      <error>\n" +
            "      </error>\n" +
            "    </error>\n" +
            "</ns:root>\n"};

        doTest(schemas, null, valid, invalid);
    }

    private void doTest(
        String[] schemas, QName docType,
        String[] validInstances, String[] invalidInstances)
        throws Exception {
        doTest(schemas, docType, validInstances, invalidInstances, true);
    }

    private void doTest(
        String[] schemas, QName docType,
        String[] validInstances, String[] invalidInstances, boolean startOnDocument)
        throws Exception {
        SchemaTypeLoader stl = makeSchemaTypeLoader(schemas);

        XmlOptions options = new XmlOptions();

        if (docType != null) {
            SchemaType docSchema = stl.findDocumentType(docType);

            assertNotNull(docSchema);

            options.setDocumentType(docSchema);
        }

        for (int i = 0; i < validInstances.length; i++) {
            XmlObject x =
                stl.parse((String) validInstances[i], null, options);

            if (!startOnDocument) {
                try (XmlCursor c = x.newCursor()) {
                    c.toFirstChild();
                    x = c.getObject();
                }
            }

            List<XmlError> xel = new ArrayList<>();

            options.setErrorListener(xel);

            boolean isValid = x.validate(options);

            if (!isValid) {
                System.err.println("Invalid doc, expected a valid doc: ");
                System.err.println("Instance(" + i + "): ");
                System.err.println(x.xmlText());
                System.err.println("Errors: ");
                for (Object o : xel) {
                    System.err.println(o);
                }
                System.err.println();
            }

            assertTrue(isValid);
        }

        for (int i = 0; i < invalidInstances.length; i++) {
            XmlObject x;

            try {
                x = stl.parse(invalidInstances[i], null, options);

                if (!startOnDocument) {
                    try (XmlCursor c = x.newCursor()) {
                        c.toFirstChild();
                        x = c.getObject();
                    }
                }

                boolean isValid = x.validate();

                if (isValid) {
                    System.err.println("Valid doc, expected a invalid doc: ");
                    System.err.println("Instance(" + i + "): ");
                    System.err.println(x.xmlText());
                    System.err.println();
                }

                assertFalse(isValid);
            } catch (XmlException ignored) {
            }
        }
    }
}
