/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

class ForeignKeyConstraintReaderTest {
	private final ForeignKeyConstraintReader reader = new ForeignKeyConstraintReader(null) {
		@Override
		protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) { return List.of(); }
	};

	private ForeignKeyConstraint read(String parentSchema, String parentName) {
		var fk = new ForeignKeyConstraint("parent_fk");
		fk.setSchemaName("child_schema"); fk.setTableName("child");
		var pairs = new FlexList<ColumnPair>();
		for (String name : List.of("b", "a")) {
			var pair = new ColumnPair(); pair.columnName = name; pair.refColumnName = name;
			pair.refTableName = parentName; pair.refSchemaName = parentSchema; pair.refCatalogName = "catalog";
			pairs.add(pair);
		}
		var map = new TripleKeyMap<String, String, String, FlexList<ColumnPair>>();
		map.put(fk.getCatalogName(), fk.getSchemaName(), fk.getName(), pairs);
		reader.setForeignKeyConstraintColumns(map, List.of(fk));
		return fk;
	}

	@Test
	void retainsDetachedParentOwnershipWithSameNamedChildColumns() {
		var fk = read("parent_schema", "Parent table");
		var child = new Table("child").setSchemaName("child_schema");
		child.getColumns().add("a"); child.getColumns().add("b");
		child.getConstraints().add(fk);
		assertEquals("Parent table", fk.getRelatedTable().getName());
		assertEquals("parent_schema", fk.getRelatedTable().getSchemaName());
		assertEquals("catalog", fk.getRelatedTable().getCatalogName());
		assertEquals(List.of("b", "a"), fk.getRelatedColumns().stream().map(c -> c.getName()).toList());
		for (var column : fk.getRelatedColumns()) {
			assertSame(fk.getRelatedTable(), column.getColumn().getTable());
			assertNotSame(child.getColumns().get(column.getName()), column.getColumn());
		}
	}

	@Test
	void resolvesCanonicalParentWhenAddedToSchemaAndKeepsSelfReferences() {
		var schema = new Schema("child_schema");
		var parent = new Table("parent"); schema.getTables().add(parent); parent.getColumns().add("a"); parent.getColumns().add("b");
		var child = new Table("child"); schema.getTables().add(child); child.getColumns().add("a"); child.getColumns().add("b");
		var fk = read("child_schema", "parent"); child.getConstraints().add(fk);
		assertSame(parent, fk.getRelatedTable());
		var self = read("child_schema", "child"); self.setName("self_fk"); child.getConstraints().add(self);
		assertSame(child, self.getRelatedTable());
	}
}
