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

import com.sqlapp.data.db.datatype.DataType;
import static org.junit.jupiter.api.Assertions.*;

public class ForeignKeyConstraintTest extends AbstractDbObjectTest<ForeignKeyConstraint> {
	@org.junit.jupiter.api.Test
	public void standaloneTableXmlPreservesReferencedOwnershipAndCompositeOrder() throws Exception {
		for (String schemaName : new String[] {"Child Schema", "Parent Schema"}) {
			Table parent = new Table("Parent Table").setSchemaName(schemaName);
			Table child = new Table("Child Table").setSchemaName("Child Schema");
			for (Table table : new Table[] {parent, child}) {
				table.getColumns().add("id", c -> c.setDataType(DataType.INT));
				table.getColumns().add("Other Key", c -> c.setDataType(DataType.INT));
			}
			var fk = new ForeignKeyConstraint("fk",
					new Column[] {child.getColumns().get("Other Key"), child.getColumns().get("id")},
					new Column[] {parent.getColumns().get("id"), parent.getColumns().get("Other Key")});
			child.getConstraints().add(fk);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(child.asXml()));
			var restoredFk = (ForeignKeyConstraint) restored.getConstraints().get("fk");
			assertEquals("Parent Table", restoredFk.getRelatedTable().getName());
			assertEquals(schemaName, restoredFk.getRelatedTableSchemaName());
			assertNotSame(restored, restoredFk.getRelatedTable());
			assertEquals(java.util.List.of("Other Key", "id"), restoredFk.getColumns().stream().map(Column::getName).toList());
			assertEquals(java.util.List.of("id", "Other Key"), restoredFk.getRelatedColumns().stream().map(ReferenceColumn::getName).toList());
			assertNotSame(restored.getColumns().get("id"), restoredFk.getRelatedColumns().get(0).getColumn());
			assertSame(restoredFk.getRelatedTable(), restoredFk.getRelatedColumns().get(0).getColumn().getTable());
		}
	}

	@org.junit.jupiter.api.Test
	public void schemaXmlResolvesParentAndSelfReferencesToCanonicalTables() throws Exception {
		Schema schema = new Schema("public");
		Table parent = new Table("parent"); parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
		Table child = new Table("child"); child.getColumns().add("id", c -> c.setDataType(DataType.INT));
		schema.getTables().add(parent); schema.getTables().add(child);
		child.getConstraints().addForeignKeyConstraint("parent_fk", child.getColumns().get("id"), parent.getColumns().get("id"));
		child.getConstraints().addForeignKeyConstraint("self_fk", child.getColumns().get("id"), child.getColumns().get("id"));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var restoredChild = restored.getTables().get("child");
		assertSame(restored.getTables().get("parent"), ((ForeignKeyConstraint) restoredChild.getConstraints().get("parent_fk")).getRelatedTable());
		assertSame(restoredChild, ((ForeignKeyConstraint) restoredChild.getConstraints().get("self_fk")).getRelatedTable());
		Table standalone = SchemaUtils.readXml(new java.io.StringReader(child.asXml()));
		var standaloneSelf = (ForeignKeyConstraint) standalone.getConstraints().get("self_fk");
		assertSame(standalone, standaloneSelf.getRelatedTable());
		assertEquals(1, standaloneSelf.getRelatedColumns().size());
	}

	@Override
	protected ForeignKeyConstraint getObject() {
		ForeignKeyConstraint fk = new ForeignKeyConstraint();
		fk.setName("FKNAME");
		fk.setRemarks("コメント");
		Table table = new Table("table1");
		Column column = new Column("A");
		column.setDataType(DataType.VARCHAR);
		table.getColumns().add(column);
		fk.addColumns(column);
		column = new Column("B");
		table.getColumns().add(column);
		fk.addColumns(column);
		//
		Table table2 = new Table("table2");
		table2.setSchemaName("schema2");
		column = new Column("RA");
		table2.getColumns().add(column);
		fk.addRelatedColumn(column);
		column = new Column("RB");
		table2.getColumns().add(column);
		fk.addRelatedColumn(column);
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
		Column column = new Column("C");
		obj2.addColumns(column);
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
