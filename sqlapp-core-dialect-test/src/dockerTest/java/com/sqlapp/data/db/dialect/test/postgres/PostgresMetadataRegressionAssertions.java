/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.test.CountingJdbc;

/** Shared real-engine regression assertions; no external credentials. */
public final class PostgresMetadataRegressionAssertions {
	private PostgresMetadataRegressionAssertions() { }
	public static void verify(Connection connection, Dialect dialect) throws Exception {
		verify(connection, dialect, null);
	}
	public static void verify(Connection connection, Dialect dialect, String tablespace) throws Exception {
		String schema = "Audit " + UUID.randomUUID().toString().replace("-", "");
		String quoted = dialect.quote(schema);
		int major = connection.getMetaData().getDatabaseMajorVersion();
		String include = major >= 11 ? " INCLUDE (note)" : "";
		String space = tablespace == null ? "" : " TABLESPACE " + dialect.quote(tablespace);
		try (var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA " + quoted);
			try {
				for (int i=0;i<3;i++) {
					sql.execute("CREATE SEQUENCE " + quoted + ".\"Seq '" + i + "\"" + (major >= 10 ? " AS integer" : "") + " START 10 INCREMENT 3 MINVALUE 1 MAXVALUE 1000 CACHE 5 CYCLE");
					sql.execute("COMMENT ON SEQUENCE " + quoted + ".\"Seq '" + i + "\" IS 'sequence comment'");
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
				reader.setReaderOptions(new ReaderOptions());
				reader.setSchemaName(schema);
				var count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection, text -> true, count);
				reader.setObjectName("Seq '0");
				assertEquals(1, reader.getAllFull(counted).size());
				assertEquals(major >= 10 ? 1 : 2, count.get());
				count.set(0);
				reader.setObjectName(null);
				var sequences = reader.getAllFull(counted);
				assertEquals(3, sequences.size());
				assertEquals(major >= 10 ? 1 : 2, count.get(), "Sequence query count must not grow with object count");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var sequence : sequences) {
					assertEquals(10, sequence.getStartValue().intValueExact());
					assertEquals(3, sequence.getIncrementBy().intValueExact());
					assertEquals(5, sequence.getCacheSize());
					assertTrue(sequence.isCycle());
					assertEquals("sequence comment", sequence.getRemarks());
					if (major >= 10) assertEquals(com.sqlapp.data.db.datatype.DataType.INT, sequence.getDataType());
					Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
					sql.execute("DROP SEQUENCE " + quoted + "." + dialect.quote(sequence.getName()));
					for (var operation : registry.createSql(restored, SqlType.CREATE)) sql.execute(operation.getSqlText());
				}
				var recreated = reader.getAllFull(connection);
				assertEquals(3, recreated.size());
				for (var original : sequences) {
					var after = recreated.stream().filter(v -> v.getName().equals(original.getName())).findFirst().orElseThrow();
					assertEquals(original.getDataType(), after.getDataType());
					assertEquals(original.getStartValue(), after.getStartValue());
					assertEquals(original.getMinValue(), after.getMinValue());
					assertEquals(original.getMaxValue(), after.getMaxValue());
					assertEquals(original.getIncrementBy(), after.getIncrementBy());
					assertEquals(original.getCacheSize(), after.getCacheSize());
					assertEquals(original.isCycle(), after.isCycle());
					assertEquals(original.getRemarks(), after.getRemarks());
				}
				sql.execute("CREATE TABLE " + quoted + ".\"Data\" (id integer, value text, note text)");
				var definitions = new LinkedHashMap<String,String>();
				String[] names = { "pattern", "expression", "gin", "unique" };
				sql.execute("CREATE INDEX pattern ON " + quoted + ".\"Data\" (value text_pattern_ops DESC NULLS LAST)" + include + " WITH (fillfactor=72)" + space + " WHERE id>0");
				sql.execute("CREATE INDEX expression ON " + quoted + ".\"Data\" (lower(value) COLLATE \"C\" text_pattern_ops)");
				sql.execute("CREATE INDEX gin ON " + quoted + ".\"Data\" USING gin (to_tsvector('simple',value)) WITH (fastupdate=off)");
				sql.execute("CREATE UNIQUE INDEX \"unique\" ON " + quoted + ".\"Data\" (id)" + include + "");
				for (var name : names) definitions.put(name, definition(connection, schema, name));
				var tableReader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				tableReader.setSchemaName(schema);
				tableReader.setObjectName("Data");
				var table = tableReader.getAllFull(connection).stream().filter(t -> t.getName().equals("Data")).findFirst().orElseThrow();
				Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				if (tablespace != null) assertEquals(tablespace, restored.getIndexes().get("pattern").getTableSpaceName());
				for (var name : names) {
					sql.execute("DROP INDEX " + quoted + "." + dialect.quote(name));
					for (var operation : registry.createSql(restored.getIndexes().get(name), SqlType.CREATE)) sql.execute(operation.getSqlText());
					assertEquals(definitions.get(name), definition(connection, schema, name), name);
				}
			} finally { sql.execute("DROP SCHEMA " + quoted + " CASCADE"); }
		}
	}
	private static String definition(Connection c, String schema, String name) throws Exception {
		try (var sql = c.prepareStatement("SELECT pg_get_indexdef(c.oid) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=?")) {
			sql.setString(1, schema); sql.setString(2, name);
			try (var rows = sql.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
		}
	}
}
