/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.StaxWriter;

public class ColumnTest extends AbstractDbObjectTest<Column> {
	@Test
	void renamesForeignKeyColumnWithoutRecursing() {
		final Schema schema = new Schema("APP");
		final Table parent = new Table("P"); schema.getTables().add(parent);
		final Column id = new Column("ID").setDataType(DataType.INT); parent.getColumns().add(id);
		parent.setPrimaryKey("PK_P", id);
		final Table child = new Table("C"); schema.getTables().add(child);
		final Column parentId = new Column("PID").setDataType(DataType.INT); child.getColumns().add(parentId);
		final var key = child.getConstraints().addForeignKeyConstraint("FK_C", parentId, id);
		parentId.setName("PARENT_ID");
		assertEquals("PARENT_ID", key.getColumns().getFirst().getName());
		id.setName("KEY");
		assertEquals("KEY", key.getRelatedColumns().getFirst().getName());
		assertEquals("KEY", parent.getPrimaryKeyConstraint().getColumns().getFirst().getName());
		parent.setName("PARENT");
		assertEquals("PARENT", key.getRelatedTableName());
		assertEquals(parent, key.getRelatedTable());
	}
	@Test
	void namedIdentitySequenceSurvivesXml() throws Exception {
		var column = new Column("ID").setDataType(DataType.INT).setIdentity(true);
		column.setSequence(new Sequence("Named.Sequence").setSchemaName("Other.Schema"));
		column.setIdentityStartValue(17L).setIdentityStep(3L);
		Column restored = SchemaUtils.readXml(new StringReader(column.asXml()));
		assertEquals("Named.Sequence", restored.getSequenceName());
		assertEquals("Other.Schema", restored.getSequenceSchemaName());
		assertEquals(17L, restored.getIdentityStartValue());
		assertEquals(3L, restored.getIdentityStep());
		assertEquals(true, restored.isIdentity());
	}

	@Override
	protected Column getObject() {
		final Column column = new Column();
		column.setName("A").setLength(1).setNullable(false).setDataType(DataType.BIT);
		column.getExtendedProperties().put("INITIAL", "TRUE");
		column.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEA");
		column.getSpecifics().put("DUMMY", "DUMMYA");
		column.getValues().add("1");
		column.getValues().add("2");
		column.getValues().add("3");
		column.setSchemaName("schemaName1");
		column.setCollation("utf8-general-ci");
		column.setMaskingFunction("default()");
		column.setCharacterSemantics(CharacterSemantics.Char);
		column.setRemarks("comment");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		column.getValues().add("");
		column.getValues().add("b");
		column.getValues().add("c");
		return column;
	}

	@Test
	public void testHandle2() throws XMLStreamException, UnsupportedEncodingException {
		final Column obj = getObject();
		//
		final StringWriter writer = new StringWriter();
		final StaxWriter stax = new StaxWriter(writer);
		stax.writeStartDocument();
		obj.writeXml(stax);
		//
		final StringReader reader = new StringReader(writer.toString());
		final Column obj2 = new Column();
		obj2.loadXml(reader);
		assertEquals(obj, obj2);
	}

	@Test
	public void testReadXml() throws XMLStreamException, IOException {
		final Column obj = SchemaUtils.readXml(this.getClass(), "column.xml");
		assertEquals(obj.getValues().size(), 6);
		final String[] array = obj.getValues().toArray(new String[0]);
		int i = 0;
		assertEquals(array[i++], "1");
		assertEquals(array[i++], "2");
		assertEquals(array[i++], "3");
		assertEquals(array[i++], "");
		assertEquals(array[i++], "b");
		assertEquals(array[i++], "c");
	}

	@Test
	public void testClonePreservesIdentitySequenceProperties() {
		final Column column = new Column("ID");
		column.setIdentity(true);
		column.setIdentityStartValue(10);
		column.setIdentityStep(3);
		column.setIdentityCacheSize(20);
		final Table table = new Table("TABLE1");
		table.getColumns().add(column);

		final Column clone = column.clone();

		assertNotSame(column.getSequence(), clone.getSequence());
		assertEquals(10L, clone.getIdentityStartValue());
		assertEquals(3L, clone.getIdentityStep());
		assertEquals(20, clone.getIdentityCacheSize());
		assertEquals(3L, table.clone().getColumns().get("ID").getIdentityStep());
	}

	@Test
	public void testCloneKeepsCheckConstraintOwnershipAndStateIndependent() {
		Table original = new Table("Original");
		Column column = new Column("ID").setDataType(DataType.INT);
		column.setCheckConstraint(new CheckConstraint("CK", "ID > 0"));
		column.getCheckConstraint().getSpecifics().put("is_not_trusted", "false");
		original.getColumns().add(column);
		Table copy = original.clone();
		CheckConstraint copied = copy.getColumns().get(0).getCheckConstraint();
		assertNotSame(column.getCheckConstraint(), copied);
		org.junit.jupiter.api.Assertions.assertSame(original, column.getCheckConstraint().getTable());
		org.junit.jupiter.api.Assertions.assertSame(copy, copied.getTable());
		copied.setEnable(false);
		copied.getSpecifics().put("is_not_trusted", "true");
		org.junit.jupiter.api.Assertions.assertTrue(column.getCheckConstraint().isEnable());
		assertEquals("false", column.getCheckConstraint().getSpecifics().get("is_not_trusted"));
		org.junit.jupiter.api.Assertions.assertFalse(original.equals(copy));
		assertEquals(State.Modified, original.diff(copy).getState());
		assertEquals(State.Modified, column.diff(copy.getColumns().get(0)).getState());
	}

	@Test
	public void testDetailedAndLegacyCheckXmlRoundTrip() throws Exception {
		Column legacy = new Column("ID").setDataType(DataType.INT).setCheck("ID > 0");
		org.junit.jupiter.api.Assertions.assertTrue(legacy.asXml().contains("check=\"ID &gt; 0\""));
		Column restored = SchemaUtils.readXml(new StringReader(legacy.asXml()));
		assertEquals(legacy, restored);
		Table table = new Table("T");
		table.getColumns().add(legacy);
		legacy.getCheckConstraint().setName("CK");
		legacy.getCheckConstraint().setEnable(false);
		legacy.getCheckConstraint().getSpecifics().put("is_not_trusted", "true");
		org.junit.jupiter.api.Assertions.assertTrue(table.asXml().contains("<checkConstraint"));
		Table restoredTable = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertEquals(table, restoredTable);
		org.junit.jupiter.api.Assertions.assertSame(restoredTable, restoredTable.getColumns().get(0).getCheckConstraint().getTable());
	}

	@Override
	protected void testDiffString(final Column obj1, final Column obj2) {
		obj2.setName("b");
		obj2.setRemarks("コメントB");
		obj2.getSpecifics().put("DUMMY", "DUMMYB");
		obj2.getSpecifics().put("TABLE_SPACE", "TABLE_SPACEB");
		final DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
