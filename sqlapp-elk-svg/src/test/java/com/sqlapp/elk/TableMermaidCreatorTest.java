/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-elk-svg.
 *
 * sqlapp-elk-svg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-elk-svg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-elk-svg.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class TableMermaidCreatorTest {

	@Test
	void preservesCompositeParallelSelfAndCrossSchemaRelations() {
		Schema a = new Schema("A");
		Schema b = new Schema("B");
		Table parent = table(a, "ITEM");
		Table child = table(b, "ITEM");
		Table isolated = table(b, "ISOLATED");
		parent.setPrimaryKey("PK_PARENT", parent.getColumns().get("ID"), parent.getColumns().get("OTHER"));
		child.getConstraints().addForeignKeyConstraint("COMPOSITE", child.getColumns().toArray(Column[]::new),
				parent.getColumns().toArray(Column[]::new));
		child.getConstraints().addForeignKeyConstraint("PARALLEL", child.getColumns().get("ID"), parent.getColumns().get("ID"));
		parent.getConstraints().addForeignKeyConstraint("SELF", parent.getColumns().get("OTHER"), parent.getColumns().get("ID"));
		parent.getConstraints().addForeignKeyConstraint("CYCLE", parent.getColumns().get("ID"), child.getColumns().get("ID"));
		String source = new TableMermaidCreator().generate(List.of(parent, child, isolated));
		assertTrue(source.contains("t0[\"A.ITEM\"]"));
		assertTrue(source.contains("t1[\"B.ITEM\"]"));
		assertTrue(source.contains("t2[\"B.ISOLATED\"]"));
		assertEquals(4, source.lines().filter(line -> line.contains(" : \"")).count());
		assertTrue(source.contains("t0 ||..o{ t1 : \"COMPOSITE\""));
		assertTrue(source.contains("t0 ||--o{ t0 : \"SELF\""));
		assertTrue(source.contains("PK, FK"));
		String subset = new TableMermaidCreator().generate(List.of(child));
		assertFalse(subset.contains("COMPOSITE"));
		assertFalse(subset.contains("A.ITEM"));
		assertEquals(source, new TableMermaidCreator().generate(List.of(parent, child, isolated)));
	}

	@Test
	void handlesNullableUniqueRelationships() {
		Schema schema = new Schema("S");
		Table parent = table(schema, "PARENT");
		Table child = table(schema, "CHILD");
		child.setPrimaryKey("PK", child.getColumns().get("ID"));
		child.getColumns().get("ID").setNotNull(false);
		child.getConstraints().addForeignKeyConstraint("FK", child.getColumns().get("ID"), parent.getColumns().get("ID"));
		String source = new TableMermaidCreator().generate(List.of(parent, child));
		assertTrue(source.contains("t0 |o--o| t1 : \"FK\""));
		assertTrue(source.contains("INT ID PK, FK\n"));
		assertFalse(source.contains("ID : INT"));
	}

	@Test
	void escapesLabelsAndKeepsAttributeTokensDistinct() {
		Table table = new Table("物理\"<>&#\\\n名");
		table.setDisplayName("論理名");
		table.getColumns().add(new Column("a b").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("a-b").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("a@b").setDataType(DataType.VARCHAR));
		table.getColumns().get(0).setDisplayName("名前\"\nテスト");
		String physical = new TableMermaidCreator().generate(List.of(table));
		assertTrue(physical.contains("物理#34;#60;#62;#38;#35;#92; 名"));
		assertTrue(physical.contains("VARCHAR a_b \"name: a b\""));
		assertTrue(physical.contains("VARCHAR a-b\n"));
		assertTrue(physical.contains("VARCHAR a_b_2 \"name: a@b\""));
		String logical = new TableMermaidCreator(NameMode.LOGICAL).generate(List.of(table));
		assertTrue(logical.contains("t0[\"論理名\"]"));
		assertTrue(logical.contains("名前__テスト \"name: 名前#34; テスト\""));
		assertEquals("erDiagram\n", new TableMermaidCreator().generate(List.of()));
		assertThrows(NullPointerException.class, () -> new TableMermaidCreator(null));
		assertThrows(NullPointerException.class, () -> new TableMermaidCreator().generate(null));
	}

	@Test
	void followsSvgColumnSelectionAndTableOverrides() {
		Table table = new Table("DETAIL");
		for (int i = 0; i < 8; i++) {
			table.getColumns().add(new Column("COL" + i).setDataType(DataType.INT));
		}
		TableSvgCreator simple = new TableSvgCreator(SVGDrawMode.SIMPLE);
		assertFalse(simple.generateMermaid(List.of(table)).contains("COL7"));
		assertTrue(new TableSvgCreator().generateMermaid(List.of(table)).contains("COL7"));
		simple.setTableNodeConsumer(node -> SVGDrawMode.NORMAL.reset(node));
		assertTrue(simple.generateMermaid(List.of(table)).contains("COL7"));
	}

	@Test
	void protectsMermaidReservedWordsAndDirectives() {
		Table table = new Table("100% direction LR");
		table.getColumns().add(new Column("FK").setDataTypeName("PK"));
		String source = new TableMermaidCreator().generate(List.of(table));
		assertTrue(source.contains("t0[\"100#37; #100;irection LR\"]"));
		assertTrue(source.contains("value_PK value_FK \"name: FK; type: PK\""));
	}

	@Test
	void emitsMultipleInheritanceAndNestedPartitionsWithoutDuplicateEdges() {
		Catalog catalog = new Catalog("CAT");
		Schema a = new Schema("A");
		Schema b = new Schema("B");
		catalog.getSchemas().add(a);
		catalog.getSchemas().add(b);
		Table parent = table(a, "BASE");
		Table other = table(b, "OTHER_BASE");
		Table child = table(b, "CHILD");
		Table partition = table(b, "PARTITION");
		Table nested = table(b, "NESTED");
		child.setDisplayName("継承先");
		child.getInherits().add(parent);
		child.getInherits().add(other);
		partition.setPartitionParent(parent, "1", "10");
		partition.getInherits().add(parent);
		nested.setPartitionParent(partition, "1", "5");
		child.getConstraints().addForeignKeyConstraint("FK", child.getColumns().get("ID"), parent.getColumns().get("ID"));
		var tables = List.of(parent, other, child, partition, nested);
		for (NameMode names : NameMode.values()) {
			for (SVGDrawMode mode : SVGDrawMode.values()) {
				String source = new TableSvgCreator(mode, names).generateMermaid(tables);
				assertTrue(source.contains("t2 }o..o{ t0 : \"inherits\""));
				assertTrue(source.contains("t2 }o..o{ t1 : \"inherits\""));
				assertTrue(source.contains("t3 }o..|| t0 : \"partition of\""));
				assertTrue(source.contains("t4 }o..|| t3 : \"partition of\""));
				assertFalse(source.contains("t3 }o..o{ t0"));
				assertTrue(source.contains("t0 ||..o{ t2 : \"FK\""));
				assertEquals(5, source.lines().filter(line -> line.contains(" : \"")).count());
				assertEquals(1, source.lines().filter(line -> line.contains("not row cardinalities")).count());
				assertTrue(source.contains(names == NameMode.LOGICAL ? "継承先" : "B.CHILD"));
			}
		}
		String subset = new TableMermaidCreator().generate(List.of(child, nested));
		assertFalse(subset.contains("inherits"));
		assertFalse(subset.contains("partition of"));
		assertFalse(subset.contains("BASE"));
	}

	@Test
	void structuralCyclesDoNotRecurseOrDuplicateEntities() {
		Schema schema = new Schema("S");
		Table a = table(schema, "A");
		Table b = table(schema, "B");
		a.getInherits().add(b);
		b.getInherits().add(a);
		String source = new TableMermaidCreator().generate(List.of(a, b, a));
		assertTrue(source.contains("t0 }o..o{ t1 : \"inherits\""));
		assertTrue(source.contains("t1 }o..o{ t0 : \"inherits\""));
		assertEquals(2, source.lines().filter(line -> line.contains("[\"")).count());
	}

	private Table table(Schema schema, String name) {
		Table table = new Table(name);
		schema.getTables().add(table);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("OTHER").setDataType(DataType.INT).setNotNull(true));
		return table;
	}
}
