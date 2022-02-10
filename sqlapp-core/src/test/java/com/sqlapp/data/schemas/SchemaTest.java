/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class SchemaTest extends AbstractDbObjectTest<Schema> {

	public static Schema getSchema(String schemaName) {
		Schema schema = new Schema(schemaName);
		schema.setProductName("hsql");
		schema.setProductMajorVersion(2);
		schema.setProductMinorVersion(0);
		schema.getTables().add(TableTest.getTable("TableA"));
		schema.getViews().add(ViewTest.getView("ViewA"));
		schema.getMviews().add(MviewTest.getMview("MViewA"));
		schema.getExternalTables().add(
				ExternalTableTest.getExternalTable("ExternalTableA"));
		schema.getMviewLogs().add(MviewLogTest.getMviewLog("MviewLogName"));
		schema.getOperators().add(OperatorTest.getOperator("VARCHAR"));
		schema.getOperatorClasses().add(
				OperatorClassTest.getOperatorClass("operatorClassA"));
		//
		Function func1=FunctionTest.getFunction("functionA");
		func1.setSpecificName("functionA_specific1");
		schema.getFunctions().add(func1);
		func1=FunctionTest.getFunction("functionA");
		func1.setSpecificName("functionA_specific2");
		schema.getFunctions().add(func1);
		schema.getProcedures().add(ProcedureTest.getProcedure("procedureA"));
		schema.getSequences().add(SequenceTest.getSequence("SequenceA"));
		schema.getTriggers().add(TriggerTest.getTrigger("triggerA"));
		schema.getTypes().add(TypeTest.getType("typeA"));
		schema.getEvents().add(EventTest.getEvent());
		schema.getSynonyms().add(SynonymTest.getSynonym("Synonym!"));
		schema.getDbLinks().add(DbLinkTest.getDbLink("DbLink!"));
		schema.getDimensions().add(DimensionTest.getDimension("dimensionA"));
		schema.getXmlSchemas().add(XmlSchemaTest.getXmlSchema("xmlSchemaA"));
		schema.setCharacterSet("utf8");
		schema.setCharacterSemantics(CharacterSemantics.Char);
		schema.setCollation("utf8_bin");
		schema.setTableSpaceName("tableSpaceA");
		schema.setIndexTableSpaceName("indexTableSpaceA");
		schema.setLobTableSpaceName("lobTableSpaceA");
		schema.setTemporaryTableSpaceName("TemporaryTableSpaceA");
		return schema;
	}

	@Override
	protected Schema getObject() {
		return getSchema("SchemaA");
	}

	@Override
	protected SchemaXmlReaderHandler getHandler() {
		return new SchemaXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Schema obj1, Schema obj2) {
		obj2.getTables().get("TableA").setTableSpaceName("tableSpaceB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
	
	@Test
	public void testToCatalog() {
		Schema cc = new Schema();
		cc.setCharacterSet("utf8");
		cc.setCharacterSemantics(CharacterSemantics.Char);
		cc.setCollation("utf8_bin");
		cc.setProductName("mysql");
		cc.setProductMajorVersion(5);
		cc.setProductMinorVersion(6);
		cc.setProductRevision(7);
		Catalog catalog=cc.toCatalog();
		assertEquals("utf8", catalog.getCharacterSet());
		assertEquals(CharacterSemantics.Char, catalog.getCharacterSemantics());
		assertEquals("utf8_bin", catalog.getCollation());
		assertEquals("mysql", catalog.getProductName());
		assertEquals(Integer.valueOf(5), catalog.getProductMajorVersion());
		assertEquals(Integer.valueOf(6), catalog.getProductMinorVersion());
		assertEquals(Integer.valueOf(7), catalog.getProductRevision());
	}
}
