/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class MySqlIndexValidationTest {
	@ParameterizedTest
	@CsvSource({ "5,1", "5,7", "8,4", "9,0" })
	void catalogRtreeTypeRecreatesAsNativeSpatialIndex(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = new Table("places");
		table.getColumns().add("location", c -> c.setDataType(DataType.GEOMETRY).setNullable(false));
		Index index = new Index("ix_location");
		index.setIndexType(IndexType.RTree);
		index.getColumns().add(table.getColumns().get("location"));
		table.getIndexes().add(index);
		String sql = create(dialect, table);
		assertTrue(sql.contains("SPATIAL INDEX ix_location"), sql);
		assertFalse(sql.contains("USING RTREE"), sql);
		SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertTrue(factory.createSql(index).get(0).getSqlText().startsWith("CREATE SPATIAL INDEX"));
	}

	@ParameterizedTest
	@CsvSource({ "5,1", "5,5", "5,7" })
	void oldFamiliesRejectFunctionalAndInvisibleIndexes(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = table();
		Index index = new Index("ix");
		index.getColumns().add("lower(code)");
		table.getIndexes().add(index);
		assertRejected(dialect, table, index);
		index.getColumns().clear();
		index.getColumns().add(table.getColumns().get("code"));
		index.setEnable(false);
		assertRejected(dialect, table, index);
		index.setEnable(true);
		assertTrue(create(dialect, table).contains("INDEX ix"));
		var unique = new UniqueConstraint("uq", false);
		unique.getColumns().add(table.getColumns().get("code"));
		unique.getIndex().setEnable(false);
		table.getConstraints().add(unique);
		assertThrows(IllegalArgumentException.class, () -> create(dialect, table));
	}

	@ParameterizedTest
	@CsvSource({ "8,0", "8,4", "9,0" })
	void modernFamiliesKeepFunctionalAndInvisibleIndexes(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = table();
		Index index = new Index("ix");
		index.getColumns().add("lower(code)").setOrder(Order.Desc);
		index.setEnable(false);
		table.getIndexes().add(index);
		assertTrue(create(dialect, table).contains("((lower(code)) DESC ) INVISIBLE"));
		SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertTrue(factory.createSql(index).get(0).getSqlText().endsWith("INVISIBLE"));
		Table original = table();
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.ALTER);
		assertTrue(alter.createDiffSql(original.diff(table)).get(0).getSqlText().contains("ADD INDEX ix"));
	}

	@ParameterizedTest
	@CsvSource({ "5,7", "8,4" })
	void rejectsUnsupportedAttributesInsteadOfDroppingThem(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = table();
		Index index = new Index("ix");
		table.getIndexes().add(index);
		assertRejected(dialect, table, index);
		var part = index.getColumns().add(table.getColumns().get("code"));
		part.setLength(0L);
		assertRejected(dialect, table, index);
		part.setLength(-1L);
		assertRejected(dialect, table, index);
		part.setLength(null);
		part.setNullsOrder(NullsOrder.NullsLast);
		assertRejected(dialect, table, index);
		part.setNullsOrder((NullsOrder) null);
		part.setIncludedColumn(true);
		assertRejected(dialect, table, index);
		part.setIncludedColumn(false);
		index.getIncludes().add(table.getColumns().get("id"));
		assertRejected(dialect, table, index);
		index.getIncludes().clear();
		index.setIndexType(IndexType.Gin);
		assertRejected(dialect, table, index);
		index.setIndexType(IndexType.FullText);
		index.setUnique(true);
		assertRejected(dialect, table, index);
		index.setUnique(false);
		part.setLength(8L);
		assertRejected(dialect, table, index);
		index.setIndexType(IndexType.Spatial);
		assertRejected(dialect, table, index);
		index.setIndexType(IndexType.BTree);
		part.setLength(8L);
		assertTrue(create(dialect, table).contains("code(8)"));
	}

	@ParameterizedTest
	@CsvSource({ "8,0", "8,4", "9,0" })
	void functionalPrefixAndSpecialIndexCombinationsFail(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = table();
		Index index = new Index("ix");
		var part = index.getColumns().add("lower(code)");
		table.getIndexes().add(index);
		part.setLength(8L);
		assertRejected(dialect, table, index);
		part.setLength(null);
		index.setIndexType(IndexType.FullText);
		assertRejected(dialect, table, index);
		index.setIndexType(IndexType.Spatial);
		assertRejected(dialect, table, index);
		index.setIndexType((IndexType) null);
		var primary = new UniqueConstraint("pk", true);
		primary.getColumns().add(table.getColumns().get("id"));
		primary.getIndex().setEnable(false);
		table.getConstraints().add(primary);
		assertThrows(IllegalArgumentException.class, () -> create(dialect, table));
	}

	private void assertRejected(Dialect dialect, Table table, Index index) {
		SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(index));
		assertThrows(IllegalArgumentException.class, () -> create(dialect, table));
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(table().diff(table)));
	}

	private String create(Dialect dialect, Table table) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		return factory.createSql(table).get(0).getSqlText();
	}

	private Table table() {
		Table table = new Table("t");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		return table;
	}

	private Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
	}
}
