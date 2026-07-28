/*
 * Copyright (C) 2007-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SystemVersioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;

class Db2ModernSchemaSqlTest extends AbstractDb2SqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 12;
	}

	@Override
	protected int getMinorVersion() {
		return 1;
	}

	@Override
	protected int getRevision() {
		return 2;
	}

	@Test
	void testNativeBoolean() {
		final Table table = new Table("FEATURE_FLAGS");
		table.setDialect(dialect);
		table.getColumns().add("ENABLED", column -> column.setDataType(DataType.BOOLEAN));

		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		final String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.replaceAll("\\s+", " ").contains("ENABLED BOOLEAN"), sql);
	}

	@Test
	void testVector() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		table.getColumns().add("FLOAT_EMBEDDING", column -> column
				.setDataType(DataType.VECTOR)
				.setVectorDimension(768)
				.setVectorElementDataType(DataType.REAL));
		table.getColumns().add("BYTE_EMBEDDING", column -> column
				.setDataType(DataType.VECTOR)
				.setVectorDimension(300)
				.setVectorElementDataType(DataType.TINYINT));

		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		final String sql = factory.createSql(table).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.matches(".*FLOAT_EMBEDDING\\s+VECTOR\\(768,\\s*FLOAT32\\s*\\).*"), sql);
		assertTrue(sql.matches(".*BYTE_EMBEDDING\\s+VECTOR\\(300,\\s*INT8\\s*\\).*"), sql);
		assertEquals(DataType.VECTOR,
				dialect.getDbDataTypes().getDbType(DataType.VECTOR).getDataType());
	}

	@Test
	void testSystemVersioning() {
		final Table table = new Table("AUDIT_LOG");
		table.setSchemaName("APP");
		table.setDialect(dialect);
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT));
		table.getColumns().add("ROW_START",
				column -> column.setDataType(DataType.TIMESTAMP).setLength(12).setNullable(false));
		table.getColumns().add("ROW_END",
				column -> column.setDataType(DataType.TIMESTAMP).setLength(12).setNullable(false));
		table.getColumns().add("TRANSACTION_ID",
				column -> column.setDataType(DataType.TIMESTAMP).setLength(12));
		table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME")
				.setPeriodType(TemporalPeriodType.SYSTEM_TIME)
				.setStartColumnName("ROW_START")
				.setEndColumnName("ROW_END"));
		table.setSystemVersioning(new SystemVersioning()
				.setPeriodName("SYSTEM_TIME")
				.setHistoryTableSchemaName("HISTORY")
				.setHistoryTableName("AUDIT_LOG_HISTORY")
				.setTransactionIdColumnName("TRANSACTION_ID"));

		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		final List<SqlOperation> operations = factory.createSql(table);
		assertEquals(2, operations.size());
		final String createSql = operations.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(createSql.contains("ROW_START TIMESTAMP(12) NOT NULL GENERATED ALWAYS AS ROW BEGIN"), createSql);
		assertTrue(createSql.contains("ROW_END TIMESTAMP(12) NOT NULL GENERATED ALWAYS AS ROW END"), createSql);
		assertTrue(createSql.contains("GENERATED ALWAYS AS TRANSACTION START ID"), createSql);
		assertTrue(createSql.contains("PERIOD SYSTEM_TIME ( ROW_START, ROW_END )"), createSql);
		final String alterSql = operations.get(1).getSqlText().replaceAll("\\s+", " ");
		assertTrue(alterSql.contains("ALTER TABLE APP.AUDIT_LOG ADD VERSIONING USE HISTORY TABLE"), alterSql);
		assertTrue(alterSql.contains("HISTORY.AUDIT_LOG_HISTORY"), alterSql);
	}
}
