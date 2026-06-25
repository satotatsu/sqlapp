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

import java.io.UnsupportedEncodingException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;

public class ForeignKeyConstraintTest extends AbstractDbObjectTest<ForeignKeyConstraint> {

	@Override
	protected ForeignKeyConstraint getObject() {
		ForeignKeyConstraint fk = new ForeignKeyConstraint();
		fk.setName("FKNAME");
		fk.setRemarks("remarksA");
		Table table = new Table("table1");
		Column columnA = new Column("A");
		columnA.setDataType(DataType.VARCHAR);
		table.getColumns().add(columnA);
		Column columnB = new Column("B");
		table.getColumns().add(columnB);
		//
		Table table2 = new Table("table2");
		table2.setSchemaName("schema2");
		Column columnRA = new Column("RA");
		table2.getColumns().add(columnRA);
		Column columnRB = new Column("RB");
		table2.getColumns().add(columnRB);
		fk.addColumn(columnA, columnRA);
		fk.addColumn(columnB, columnRB);
		//
		table.getConstraints().add(fk);
		assertEquals(1, table2.getChildRelations().size());
		//
		assertEquals(table.getName(), fk.getTableName());
		assertEquals(table.getSchemaName(), fk.getSchemaName());
		assertEquals(table.getName(), fk.getTable().getName());
		assertEquals(table.getSchemaName(), fk.getTable().getSchemaName());
		//
		assertEquals(table2.getName(), fk.getRelatedTableName());
		assertEquals(table2.getSchemaName(), fk.getRelatedTableSchemaName());
		assertEquals(table2.getName(), fk.getRelatedTable().getName());
		assertEquals(table2.getSchemaName(), fk.getRelatedTable().getSchemaName());
		//
		return fk;
	}

	@Override
	protected ForeignKeyConstraintXmlReaderHandler getHandler() {
		return new ForeignKeyConstraintXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ForeignKeyConstraint obj1, ForeignKeyConstraint obj2) {
		obj2.setName("b");
		Column columnC = new Column("C");
		Column columnRC = new Column("RC");
		obj2.addColumn(columnC, columnRC);
		obj2.setRemarks("remarksB");
		DbObjectDifference diff = obj1.diff(obj2);
		String text = diff.toString();
		String expected = """
				C:foreignKeyConstraint[name=(FKNAME -> b), columns=(
					+:column[name=C]
					), relatedColumns=(
					+:column[name=RC]
					), remarks=(remarksA -> remarksB)]""";
		assertEquals(expected, text.trim());
	}

	protected void testDiffString(final DbObjectDifference diff) {
		testDiffString(CommonUtils.initCap(this.getClass().getSimpleName().replace("Test", "")), diff);
	}

	@Test
	public void testXmlFormat() throws XMLStreamException, UnsupportedEncodingException {
		ForeignKeyConstraint object = getObject();
		String text = object.asXml();
		String expected = """
				<foreignKeyConstraint xml:space="preserve" name="FKNAME" remarks="remarksA">
					<columns>
						<column name="A"/>
						<column name="B"/>
					</columns>
					<relatedTable name="table2" schemaName="schema2">
						<columns>
							<column name="RA"/>
							<column name="RB"/>
						</columns>
					</relatedTable>
				</foreignKeyConstraint>""";
		assertEquals(expected, text.trim());
	}
}
