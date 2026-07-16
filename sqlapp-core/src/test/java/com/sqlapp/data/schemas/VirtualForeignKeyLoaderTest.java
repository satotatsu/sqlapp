/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.exceptions.InvalidTextException;
import com.sqlapp.util.CommonUtils;

class VirtualForeignKeyLoaderTest {

	@Test
	void testEmpty() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchema(true);
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		loader.loadInternal(catalog, "");
		loader.loadInternal(catalog, "tabB(ID)->tabA");
		Table tabB = schema.getTables().get("tabB");
		List<ForeignKeyConstraint> fks = tabB.getConstraints().getForeignKeyConstraints();
		assertEquals(2, fks.size());
		ForeignKeyConstraint fk = fks.stream().filter(f -> f.isVirtual()).findFirst().get();
		assertEquals(true, fk.isVirtual());
		assertEquals(1, fk.getColumns().size());
		int i = 0;
		assertEquals("ID", fk.getColumns().get(i++).getName());
		i = 0;
		assertEquals("ID", fk.getRelatedColumns().get(i++).getName());
	}

	@Test
	void testDuplicate() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchema(true);
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		InvalidTextException exception = assertThrows(InvalidTextException.class, () -> {
			loader.loadInternal(catalog, "tabB(PARENT_ID)->tabA");
		});
		assertEquals("Duplicate foreign Key. [lineNo=1,line=tabB(PARENT_ID)->tabA]", exception.getMessage());
	}

	@Test
	void testAutoFK_PARENT_ID() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchema(false);
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		loader.loadInternal(catalog, "tabB->tabA");
		Table tabB = schema.getTables().get("tabB");
		List<ForeignKeyConstraint> fks = tabB.getConstraints().getForeignKeyConstraints();
		assertEquals(1, fks.size());
		ForeignKeyConstraint fk = CommonUtils.first(fks);
		assertEquals(true, fk.isVirtual());
		assertEquals(1, fk.getColumns().size());
		int i = 0;
		assertEquals("PARENT_ID", fk.getColumns().get(i++).getName());
		i = 0;
		assertEquals("ID", fk.getRelatedColumns().get(i++).getName());
	}

	@Test
	void testAutoFK_ID() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchema3();
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		loader.loadInternal(catalog, "tabB->tabA");
		Table tabB = schema.getTables().get("tabB");
		List<ForeignKeyConstraint> fks = tabB.getConstraints().getForeignKeyConstraints();
		assertEquals(1, fks.size());
		ForeignKeyConstraint fk = CommonUtils.first(fks);
		assertEquals(true, fk.isVirtual());
		assertEquals(1, fk.getColumns().size());
		int i = 0;
		assertEquals("ID", fk.getColumns().get(i++).getName());
		i = 0;
		assertEquals("ID", fk.getRelatedColumns().get(i++).getName());
	}

	@Test
	void testAutoFK() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchemaNoFK();
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		loader.loadInternal(catalog, "tabB->tabA");
		Table tabB = schema.getTables().get("tabB");
		List<ForeignKeyConstraint> fks = tabB.getConstraints().getForeignKeyConstraints();
		assertEquals(1, fks.size());
		ForeignKeyConstraint fk = CommonUtils.first(fks);
		assertEquals(true, fk.isVirtual());
		assertEquals(3, fk.getColumns().size());
		int i = 0;
		assertEquals("ID", fk.getColumns().get(i++).getName());
		assertEquals("CODE", fk.getColumns().get(i++).getName());
		assertEquals("CODE2", fk.getColumns().get(i++).getName());
		i = 0;
		assertEquals("ID", fk.getRelatedColumns().get(i++).getName());
		assertEquals("CODE", fk.getRelatedColumns().get(i++).getName());
		assertEquals("CODE2", fk.getRelatedColumns().get(i++).getName());
	}

	@Test
	void testAutoFK2() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		Schema schema = getSchemaNoFK();
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		loader.loadInternal(catalog, "tabB(CODE,CODE2,ID)->tabA");
		Table tabB = schema.getTables().get("tabB");
		List<ForeignKeyConstraint> fks = tabB.getConstraints().getForeignKeyConstraints();
		assertEquals(1, fks.size());
		ForeignKeyConstraint fk = CommonUtils.first(fks);
		assertEquals(true, fk.isVirtual());
		assertEquals(3, fk.getColumns().size());
		int i = 0;
		assertEquals("CODE", fk.getColumns().get(i++).getName());
		assertEquals("CODE2", fk.getColumns().get(i++).getName());
		assertEquals("ID", fk.getColumns().get(i++).getName());
		i = 0;
		assertEquals("CODE", fk.getRelatedColumns().get(i++).getName());
		assertEquals("CODE2", fk.getRelatedColumns().get(i++).getName());
		assertEquals("ID", fk.getRelatedColumns().get(i++).getName());
	}

	private Schema getSchema(boolean withFk) {
		Schema schema = new Schema();
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.setPrimaryKey(table.getColumns().get("ID"));
		schema.getTables().add(table);
		//
		Table tableb = new Table("tabB");
		tableb.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.setPrimaryKey(tableb.getColumns().get("ID"));
		if (withFk) {
			addForeignKey(tableb, table, "PARENT_ID");
		}
		schema.getTables().add(tableb);
		return schema;
	}

	private Schema getSchemaNoFK() {
		Schema schema = new Schema();
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("CODE");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.getColumns().add(c -> {
			c.setName("CODE2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.setPrimaryKey(table.getColumns().get("ID"), table.getColumns().get("CODE"),
				table.getColumns().get("CODE2"));
		schema.getTables().add(table);
		//
		Table tableb = new Table("tabB");
		tableb.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("CODE");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.getColumns().add(c -> {
			c.setName("CODE2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.getColumns().add(c -> {
			c.setName("CODE3");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.setPrimaryKey(tableb.getColumns().get("ID"), tableb.getColumns().get("CODE"),
				table.getColumns().get("CODE2"), tableb.getColumns().get("CODE3"));
		schema.getTables().add(tableb);
		return schema;
	}

	private Schema getSchema3() {
		Schema schema = new Schema();
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.setPrimaryKey(table.getColumns().get("ID"));
		schema.getTables().add(table);
		//
		Table tableb = new Table("tabB");
		tableb.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("PARENT_ID2");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.setPrimaryKey(tableb.getColumns().get("ID"));
		schema.getTables().add(tableb);
		return schema;
	}

	private void addForeignKey(Table table, Table parent, String columnName) {
		String parentColumnName = CommonUtils.first(parent.getPrimaryKeyConstraint().getColumns()).getName();
		table.getConstraints().addForeignKeyConstraint("fk_" + table.getName(), table.getColumns().get(columnName),
				parent.getColumns().get(parentColumnName));

	}
}
