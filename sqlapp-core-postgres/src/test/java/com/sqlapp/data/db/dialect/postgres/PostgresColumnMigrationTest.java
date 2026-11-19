package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.ColumnMigration;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class PostgresColumnMigrationTest {
private Column timestamp() {
		return new Column("modified_at").setDataType(DataType.TIMESTAMPVERSION)
				.setDataTypeName("TIMESTAMP").setLength(6);
	}

	@Test
	void mapsNativeTimestampAndCurrentTimeDefault() {
		var dialect = DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, 0);
		var source = timestamp().setDefaultValue("CURRENT_TIMESTAMP(6)");
		var result = ColumnMigration.convert(source, dialect).requireComplete();
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, result.getDataType());
		assertEquals(6L, result.getLength());
		assertEquals("statement_timestamp()", result.getDefaultValue());
		assertEquals(DataType.TIMESTAMPVERSION, source.getDataType());
		assertEquals("CURRENT_TIMESTAMP(6)", source.getDefaultValue());
		var ddl = dialect.createSqlBuilder().definition(result, false).toString()
				.toUpperCase(java.util.Locale.ROOT);
		assertTrue(ddl.contains("TIMESTAMP(6) WITH TIME ZONE"), ddl);
		assertTrue(ddl.contains("DEFAULT STATEMENT_TIMESTAMP()"), ddl);
	}

	@Test
	void requiresReviewOfAutomaticUpdatesAndLocalDefaults() {
		var dialect = DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, 0);
		var source = timestamp().setDefaultValue("'2026-11-19 10:00:00'")
				.setOnUpdate("CURRENT_TIMESTAMP(6)");
		var result = ColumnMigration.convert(source, dialect);
		assertEquals(2, result.issues().size());
		assertNull(result.candidate().getOnUpdate());
		assertNull(result.candidate().getDefaultValue());
		assertThrows(IllegalStateException.class, result::requireComplete);
		assertEquals("CURRENT_TIMESTAMP(6)", source.getOnUpdate());
		assertThrows(IllegalStateException.class, () -> ColumnMigration.convert(
				new Column("ordinary").setDataType(DataType.TIMESTAMP), dialect).requireComplete());
	}
}
