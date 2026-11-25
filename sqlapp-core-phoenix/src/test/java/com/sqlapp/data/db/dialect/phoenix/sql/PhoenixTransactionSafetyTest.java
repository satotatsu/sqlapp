/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixTransactionSafetyTest {
	@Test
	void recreatesFromExplicitTransactionalDdlAndGuardsExistingTableChanges() throws Exception {
		final var table = table();
		table.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, true);
		table.getSpecifics().put(PhoenixDdlOptions.TRANSACTION_PROVIDER, "OMID");
		final String ddl = "CREATE TABLE Events (ID INTEGER PRIMARY KEY) TRANSACTIONAL=true, TRANSACTION_PROVIDER='OMID'";
		table.getSpecifics().put(PhoenixCreateTableFactory.TRANSACTIONAL_DEFINITION, ddl);
		final var index = new Index("events_idx");
		index.getColumns().add("ID");
		final String indexDdl = "CREATE INDEX events_idx ON Events (ID)";
		index.getSpecifics().put(PhoenixCreateIndexFactory.NATIVE_DEFINITION, indexDdl);
		table.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry();
		final var operations = registry.createSql(restored, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertEquals(ddl, operations.get(0).getSqlText());
		assertEquals(indexDdl, operations.get(1).getSqlText());
		final var originalSchema = new Schema("APP");
		final var targetSchema = originalSchema.clone();
		targetSchema.getTables().add(restored);
		final var migration = registry.createSql(originalSchema.diff(targetSchema), SqlType.ALTER);
		assertEquals(2, migration.size());
		assertEquals(ddl, migration.get(0).getSqlText());
		assertEquals(indexDdl, migration.get(1).getSqlText());
		final var tuning = restored.clone();
		tuning.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(restored.diff(tuning), SqlType.ALTER));
		final var indexOnly = restored.clone();
		indexOnly.getIndexes().clear();
		assertEquals(SqlType.DROP, registry.createSql(restored.diff(indexOnly), SqlType.ALTER).get(0).getSqlType());
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect4_3.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
		final var changed = restored.clone();
		changed.getColumns().add(new Column("extra").setDataType(DataType.INT));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(restored.diff(changed), SqlType.ALTER));
		changed.getSpecifics().remove(PhoenixCreateTableFactory.TRANSACTIONAL_DEFINITION);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(restored.diff(changed), SqlType.ALTER));
	}

	private Table table() {
		final var table = new Table("Events");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		return table;
	}

	@Test
	void keepsExplicitNontransactionalAndNullStorageOptionsThroughXmlAndCreate() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect4_7, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3_1)) {
			for (boolean value : new boolean[] { true, false }) {
				final var table = table();
				table.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, false);
				table.getSpecifics().put(PhoenixDdlOptions.STORE_NULLS, value);
				final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				final String sql = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0)
						.getSqlText();
				assertTrue(sql.contains("TRANSACTIONAL=FALSE"));
				assertTrue(sql.contains("STORE_NULLS=" + (value ? "TRUE" : "FALSE")));
			}
		}
		final var table = table();
		table.getSpecifics().put(PhoenixDdlOptions.STORE_NULLS, true);
		assertTrue(DialectHolder.defaultDialect4_3.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText().contains("STORE_NULLS=TRUE"));
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		table.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, false);
		assertThrows(UnsupportedOperationException.class,
				() -> DialectHolder.defaultDialect4_3.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}

	@Test
	void refusesTransactionalRecreationInsteadOfGuessingTheProviderOrDowngrading() {
		final var registry = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry();
		for (String provider : new String[] { null, "OMID", "TEPHRA", "NOTAVAILABLE", "unknown" }) {
			final var table = table();
			table.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, true);
			if (provider != null)
				table.getSpecifics().put(PhoenixDdlOptions.TRANSACTION_PROVIDER, provider);
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table, SqlType.CREATE));
		}
		final var contradictory = table();
		contradictory.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, false);
		contradictory.getSpecifics().put(PhoenixDdlOptions.TRANSACTION_PROVIDER, "OMID");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(contradictory, SqlType.CREATE));
	}

	@Test
	void rejectsTransactionChangesButAllowsUnchangedTransactionalTablesToAddColumns() {
		final var registry = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry();
		for (String key : List.of(PhoenixDdlOptions.TRANSACTIONAL, PhoenixDdlOptions.TRANSACTION_PROVIDER)) {
			final var original = table();
			final var target = original.clone();
			target.getSpecifics().put(key, key.equals(PhoenixDdlOptions.TRANSACTION_PROVIDER) ? "OMID" : "true");
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(target.diff(original), SqlType.ALTER));
		}
		final var original = table();
		original.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, true);
		original.getSpecifics().put(PhoenixDdlOptions.TRANSACTION_PROVIDER, "OMID");
		final var target = original.clone();
		target.getColumns().add(new Column("EXTRA").setDataType(DataType.INT));
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
	}

	@Test
	void changesStoreNullsInBothDirectionsAndRejectsAmbiguousRemoval() {
		final var original = table();
		original.getSpecifics().put(PhoenixDdlOptions.STORE_NULLS, false);
		final var target = original.clone();
		target.getSpecifics().put(PhoenixDdlOptions.STORE_NULLS, true);
		final var registry = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.endsWith("SET STORE_NULLS=TRUE"));
		assertTrue(registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText()
				.endsWith("SET STORE_NULLS=FALSE"));
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect4_9
				.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().remove(PhoenixDdlOptions.STORE_NULLS);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}
}
