/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixDdlOptionsTest {
	@Test
	void checksSaltBoundariesForTablesAndIndexes() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (int buckets : new int[] { 1, 256 }) {
			final var table = table();
			table.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, buckets);
			assertTrue(
					registry.createSql(table, SqlType.CREATE).get(0).getSqlText().contains("SALT_BUCKETS=" + buckets));
		}
		for (int buckets : new int[] { -1, 257 }) {
			final var table = table();
			final var index = new Index("idx");
			index.getColumns().add("id");
			index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, buckets);
			table.getIndexes().add(index);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		}
	}

	private Table table() {
		final var table = new Table("events");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		return table;
	}

	@Test
	void retainsTablePropertiesThroughXmlAndEscapesStringLiterals() throws Exception {
		final var table = table();
		table.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, "16");
		table.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "true");
		table.getSpecifics().put(PhoenixDdlOptions.MULTI_TENANT, "false");
		table.getSpecifics().put(PhoenixDdlOptions.DEFAULT_COLUMN_FAMILY, "a'b");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(
				sql.contains("SALT_BUCKETS=16, IMMUTABLE_ROWS=TRUE, MULTI_TENANT=FALSE, DEFAULT_COLUMN_FAMILY='a''b'"),
				sql);
		final String defaults = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(table(), SqlType.CREATE).get(0).getSqlText();
		assertFalse(defaults.contains("SALT_BUCKETS"));
		assertFalse(defaults.contains("IMMUTABLE_ROWS"));
	}

	@Test
	void qualifiesIndexKeysAndIncludesAndPermitsUnsaltedIndexOverride() throws Exception {
		final var table = table();
		final var payload = new Column("payload").setDataType(DataType.VARCHAR);
		payload.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
		table.getColumns().add(payload);
		final var index = new Index("idx");
		index.getColumns().add("payload", Order.Desc);
		index.getIncludes().add("id");
		index.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, "0");
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setQuateColumnName(true);
		final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"details\".\"payload\" DESC"), sql);
		assertTrue(sql.contains("SALT_BUCKETS=0"), sql);
		final var reversed = restored.getIndexes().get(0);
		reversed.getColumns().clear();
		reversed.getColumns().add("id");
		reversed.getIncludes().clear();
		reversed.getIncludes().add("payload");
		final String include = registry.createSql(reversed, SqlType.CREATE).get(0).getSqlText();
		assertTrue(include.matches("(?s).*INCLUDE \\(\\s*\"details\"\\.\"payload\"\\s*\\).*"), include);
	}

	@Test
	void rejectsInvalidPropertiesAndUnsupportedPropertyAlterations() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String salt : new String[] { "-1", "0", "257", "abc", "1; DROP TABLE t" }) {
			final var table = table();
			table.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, salt);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE), salt);
		}
		final var table = table();
		table.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "maybe");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getSpecifics().clear();
		table.getSpecifics().put(PhoenixDdlOptions.DEFAULT_COLUMN_FAMILY, " ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		final var original = table();
		final var target = original.clone();
		target.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, "4");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}
}
