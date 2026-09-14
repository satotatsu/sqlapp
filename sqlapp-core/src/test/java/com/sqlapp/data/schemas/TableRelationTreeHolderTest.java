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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.exceptions.MultipleRootTablesException;
import com.sqlapp.util.CommonUtils;

class TableRelationTreeHolderTest {

	@Test
	void test() {
		TableRelationTreeHolder holder = new TableRelationTreeHolder(getTables());
		TableRelation tableRelation = holder.findFirst(trel -> "tabA".equals(trel.getTable().getName())).get();
		assertNull(tableRelation.getParent());
		assertEquals(2, tableRelation.getChildren().size());
		int i = 0;
		assertEquals("tabB", tableRelation.getChildren().get(i++).getTable().getName());
		assertEquals("tabD", tableRelation.getChildren().get(i++).getTable().getName());

		for (TableRelation tableRel : holder.getRelationTree().values()) {
			System.out.println("tableRel=" + tableRel);
		}
	}

	@Test
	void selectsTheRequestedForeignKeyWhenAChildHasMultipleParents() {
		List<Table> tables = getTables();
		Table child = tables.get(2);
		ForeignKeyConstraint selected = child.getConstraints().getForeignKeyConstraints().getFirst();
		Table alternativeParent = tables.getFirst();
		child.getConstraints().addForeignKeyConstraint("fk_alternative", child.getColumns().get("ID"),
				alternativeParent.getColumns().get("ID"));

		TableRelationTreeHolder holder = new TableRelationTreeHolder(tables,
				foreignKey -> foreignKey.getTable() != child || foreignKey == selected);

		assertEquals("tabB", holder.getTableRelation(child).getParent().getName());
	}

	@Test
	void ignoresSelfReferenceToAnEquivalentTableInstance() {
		Table table = new Table("tabA");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("ID"));
		Table equivalent = new Table("TABA");
		equivalent.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getConstraints().addForeignKeyConstraint("fk_self",
				table.getColumns().get("PARENT_ID"), equivalent.getColumns().get("ID"));

		TableRelationTreeHolder holder = new TableRelationTreeHolder(table);

		TableRelation relation = holder.getTableRelation(table);
		assertEquals(table, relation.getTable());
		assertNull(relation.getParent());
	}

	@Test
	void ignoresAnUnresolvedForeignKeyOutsideTheTree() {
		Table table = new Table("tabA");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("ID"));
		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("fk_external");
		foreignKey.getColumns().add(table.getColumns().get("PARENT_ID"));
		foreignKey.setRelatedTableName("missing_parent");
		foreignKey.getRelatedColumns().add("ID");
		table.getConstraints().add(foreignKey);

		TableRelationTreeHolder holder = new TableRelationTreeHolder(table);

		assertNull(holder.getTableRelation(table).getParent());
	}

	@Test
	void keepsSameSchemaAndTableNamesDistinctAcrossCatalogs() {
		Catalog firstCatalog = new Catalog("CATALOG1");
		Schema firstSchema = new Schema("PUBLIC");
		firstCatalog.getSchemas().add(firstSchema);
		Table first = new Table("MEMBER");
		firstSchema.getTables().add(first);

		Catalog secondCatalog = new Catalog("CATALOG2");
		Schema secondSchema = new Schema("PUBLIC");
		secondCatalog.getSchemas().add(secondSchema);
		Table second = new Table("MEMBER");
		secondSchema.getTables().add(second);

		assertThrows(MultipleRootTablesException.class,
				() -> new TableRelationTreeHolder(first, second));
	}

	@Test
	void resolvesAParentRepresentedByAnEquivalentTableInstance() {
		Table parent = new Table("PARENT");
		parent.getColumns().add(new Column("ID").setDataType(DataType.INT));
		parent.setPrimaryKey(parent.getColumns().get("ID"));
		Table equivalentParent = new Table("parent");
		equivalentParent.getColumns().add(new Column("id").setDataType(DataType.INT));
		Table child = new Table("CHILD");
		child.getColumns().add(new Column("ID").setDataType(DataType.INT));
		child.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		child.setPrimaryKey(child.getColumns().get("ID"));
		child.getConstraints().addForeignKeyConstraint("fk_parent",
				child.getColumns().get("PARENT_ID"), equivalentParent.getColumns().get("id"));

		TableRelationTreeHolder holder = new TableRelationTreeHolder(parent, child);

		assertEquals(parent, holder.getTableRelation(child).getParentTableRelation().getTable());
	}

	private List<Table> getTables() {
		List<Table> list = CommonUtils.list();
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
		list.add(table);
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
		tableb.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tableb, table, "PARENT_ID");
		list.add(tableb);
		//
		Table tablec = new Table("tabC");
		tablec.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tablec.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tablec.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tablec.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tablec, tableb, "PARENT_ID");
		list.add(tablec);
		//
		Table tabled = new Table("tabD");
		tabled.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tabled.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tabled.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tabled.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tabled, table, "PARENT_ID");
		list.add(tabled);
		return list;
	}

	private void addForeignKey(Table table, Table parent, String columnName) {
		String parentColumnName = CommonUtils.first(parent.getPrimaryKeyConstraint().getColumns()).getName();
		table.getConstraints().addForeignKeyConstraint("fk_" + table.getName(), table.getColumns().get(columnName),
				parent.getColumns().get(parentColumnName));

	}

}
