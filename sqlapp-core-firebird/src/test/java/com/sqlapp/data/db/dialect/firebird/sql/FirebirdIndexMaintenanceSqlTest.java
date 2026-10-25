/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdIndexMaintenanceSqlTest {
	private Index index() {
		Table table = new Table("t");
		Index index = new Index("quoted \" index");
		table.getIndexes().add(index);
		index.getColumns().add("V");
		return index;
	}

	@Test
	void rejectsUnrenderedIndexTypesAndOptions() {
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		for (IndexType type : new IndexType[] { IndexType.Hash, IndexType.Gin, IndexType.Gist }) {
			Index index = index().setIndexType(type);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		}
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(index().setCompression(true), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new Index("missing table"), SqlType.CREATE));
		assertFalse(registry.createSql(index().setIndexType(IndexType.BTree), SqlType.CREATE).isEmpty());
	}

	@Test
	void namedConstraintIndexIsNotCreatedTwice() {
		Index index = index();
		UniqueConstraint constraint = new UniqueConstraint("different constraint", true);
		constraint.getSpecifics().put("firebird.indexName", index.getName());
		index.getTable().getConstraints().add(constraint);
		assertTrue(
				DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(index, SqlType.CREATE).isEmpty());
	}

	@Test
	void rebuildActivatesAnIndexWithoutChangingTheModel() {
		Index index = index().setEnable(false);
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var sql = registry.createSql(index, SqlType.REBUILD);
		assertEquals(1, sql.size());
		assertEquals("ALTER INDEX \"quoted \"\" index\" ACTIVE", sql.get(0).getSqlText());
		assertFalse(index.isEnable());
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(index.diff(index.clone().setUnique(true)), SqlType.REBUILD));
	}

	@Test
	void constraintIndexesAllowRebuildButCannotBeDeactivated() {
		Index index = index();
		UniqueConstraint constraint = new UniqueConstraint("pk", true);
		constraint.getSpecifics().put("firebird.indexName", index.getName());
		index.getTable().getConstraints().add(constraint);
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertTrue(registry.createSql(index, SqlType.ALTER).get(0).getSqlText().endsWith(" ACTIVE"));
		assertTrue(registry.createSql(index, SqlType.REBUILD).get(0).getSqlText().endsWith(" ACTIVE"));
		index.setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.ALTER));
	}

	@Test
	void tableCreationComposesIndexStateCommentsAndConstraintSuppression() {
		Table table = new Table("t");
		table.getColumns().add(new Column("V").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
		UniqueConstraint constraint = new UniqueConstraint("pk", true);
		constraint.getColumns().add("V");
		constraint.getSpecifics().put("firebird.indexName", "pk index");
		table.getConstraints().add(constraint);
		Index primary = new Index("pk index");
		primary.getColumns().add("V");
		table.getIndexes().add(primary);
		Index ordinary = new Index("ordinary").setEnable(false).setRemarks("remark");
		ordinary.getColumns().add("V");
		table.getIndexes().add(ordinary);
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		assertEquals(4, sql.size());
		assertTrue(sql.get(1).getSqlText().contains("INDEX \"ordinary\""));
		assertTrue(sql.get(2).getSqlText().startsWith("COMMENT ON INDEX"));
		assertTrue(sql.get(3).getSqlText().endsWith(" INACTIVE"));
	}
}
