/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class FirebirdTemporaryIndexSqlTest {
	private Table table() {
		Table table = new Table("quoted table").setRemarks("table remark");
		table.getColumns().add(new Column("V").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true)
				.setRemarks("column remark"));
		Index index = new Index("index name").setEnable(false).setRemarks("index remark");
		index.getColumns().add("V");
		table.getIndexes().add(index);
		return table;
	}

	private SqlFactory<Table> factory(Table table) {
		SqlFactory<Table> factory = DialectHolder.defaultDialect25.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.CREATE_TEMPORARY);
		factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		factory.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		return factory;
	}

	@Test
	void usesPhysicalNameForIndexesAndCommentsWithoutMutatingTheModel() {
		Table table = table();
		Table original = table.clone();
		var sql = factory(table).createSql(table);
		assertEquals(6, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("\"tmp_quoted table_suffix\""));
		assertTrue(sql.get(1).getSqlText().contains("ON \"tmp_quoted table_suffix\""));
		assertTrue(sql.get(2).getSqlText().startsWith("COMMENT ON INDEX"));
		assertTrue(sql.get(3).getSqlText().endsWith(" INACTIVE"));
		assertEquals("COMMENT ON TABLE \"tmp_quoted table_suffix\" IS 'table remark'", sql.get(4).getSqlText());
		assertEquals("COMMENT ON COLUMN \"tmp_quoted table_suffix\".V IS 'column remark'", sql.get(5).getSqlText());
		assertEquals(original, table);
	}

	@Test
	void doesNotDuplicateNamedConstraintIndexes() {
		Table table = table();
		table.getIndexes().get(0).setEnable(true);
		UniqueConstraint key = new UniqueConstraint("pk", true);
		key.getColumns().add("V");
		key.getSpecifics().put("firebird.indexName", "index name");
		table.getConstraints().add(key);
		var sql = factory(table).createSql(table);
		assertEquals(3, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("USING ASCENDING INDEX \"index name\""));
	}
}
