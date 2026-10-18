/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleIotDefinitionTest {
	private Table table(String name) {
		Table table = new Table(name).setSchemaName("APP");
		table.getColumns().add("ID");
		table.getSpecifics().put("oracle.table.organization", "INDEX");
		table.getSpecifics().put("oracle.table.iotComplex", true);
		return table;
	}

	private String ddl(String name) {
		return "CREATE TABLE APP." + name
				+ " (ID NUMBER PRIMARY KEY, BODY VARCHAR2(4000))\nORGANIZATION INDEX COMPRESS 1 PCTTHRESHOLD 20 INCLUDING ID OVERFLOW TABLESPACE DATA_OVERFLOW";
	}

	private OracleTableReader reader(int[] calls, int failure) {
		return reader(calls, failure, false);
	}

	private Table composite(String name) {
		Table table = table(name);
		table.getSpecifics().clear();
		table.toPartitioning().getPartitioning().setPartitioningType(com.sqlapp.data.schemas.PartitioningType.Range)
				.setSubPartitioningType(com.sqlapp.data.schemas.PartitioningType.Hash);
		return table;
	}

	private String compositeDdl(String name) {
		return "CREATE TABLE APP." + name + " (ID NUMBER) PARTITION BY RANGE(ID) SUBPARTITION BY HASH(ID) "
				+ "(PARTITION P1 VALUES LESS THAN(100) (SUBPARTITION S11, SUBPARTITION S12))";
	}

	private OracleTableReader reader(int[] calls, int failure, boolean composite) {
		return new OracleTableReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				calls[0]++;
				try {
					for (String name : List.of("T1", "T2")) {
						if (failure != 0 && name.equals("T2"))
							throw new SQLException("metadata failure", "99999", failure);
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String key) {
								return switch (key) {
								case "OWNER" -> "APP";
								case "TABLE_NAME" -> name;
								case "TABLE_DEFINITION" -> composite ? compositeDdl(name) : ddl(name);
								default -> null;
								};
							}

							@Override
							public Object getObject(String key) {
								return getString(key);
							}
						});
					}
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
	}

	@Test
	void compositeDefinitionsAreBatchedAndRecreatedAfterXml() throws Exception {
		Table first = composite("T1"), second = composite("T2");
		int[] calls = { 0 };
		reader(calls, 0, true).readTableDefinitions(null, List.of(first, second, new Table("HEAP")));
		assertEquals(1, calls[0]);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		var operations = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(compositeDdl("T1"), operations.get(0).getSqlText());
		restored.setSchemaName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> OracleTableDefinition.get(restored));
	}

	@Test
	void compositePermissionDenialRetainsCatalogAndDiscardsPartialDdl() {
		for (int code : new int[] { 1031, 31603 }) {
			Table first = composite("T1");
			int[] calls = { 0 };
			reader(calls, code, true).readTableDefinitions(null, List.of(first, composite("T2")));
			assertEquals(1, calls[0]);
			assertFalse(first.getSpecifics().containsKey(OracleTableDefinition.DEFINITION));
			assertEquals(com.sqlapp.data.schemas.PartitioningType.Hash,
					first.getPartitioning().getSubPartitioningType());
		}
	}

	@Test
	void compositeFailuresOtherThanAccessDenialPropagate() {
		assertThrows(RuntimeException.class, () -> reader(new int[] { 0 }, 942, true).readTableDefinitions(null,
				List.of(composite("T1"), composite("T2"))));
	}

	@Test
	void complexIotDefinitionsAreBatchedAndRecreatedAfterXml() throws Exception {
		Table first = table("T1"), second = table("T2");
		int[] calls = { 0 };
		reader(calls, 0).readTableDefinitions(null, List.of(first, second));
		assertEquals(1, calls[0]);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		var operations = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(1, operations.size());
		assertEquals(ddl("T1"), operations.get(0).getSqlText());
	}

	@Test
	void permissionDenialDiscardsPartialResultsAndRetainsCatalogMetadata() {
		for (int code : new int[] { 1031, 31603 }) {
			Table first = table("T1");
			int[] calls = { 0 };
			reader(calls, code).readTableDefinitions(null, List.of(first, table("T2")));
			assertEquals(1, calls[0]);
			assertFalse(first.getSpecifics().containsKey(OracleTableDefinition.DEFINITION));
			assertEquals("INDEX", first.getSpecifics().get("oracle.table.organization"));
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(first, SqlType.CREATE));
		}
	}

	@Test
	void unrelatedFailurePropagatesAndHeapTablesNeedNoQuery() {
		assertThrows(RuntimeException.class,
				() -> reader(new int[] { 0 }, 942).readTableDefinitions(null, List.of(table("T1"), table("T2"))));
		int[] calls = { 0 };
		reader(calls, 0).readTableDefinitions(null, List.of(new Table("HEAP")));
		assertEquals(0, calls[0]);
	}

	@Test
	void modelChangesCannotReplayStaleDdl() {
		Table table = table("T1");
		OracleTableDefinition.set(table, ddl("T1"));
		table.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}

	@Test
	void secondaryIndexesAreGeneratedSeparately() {
		Table table = table("T1");
		OracleTableDefinition.set(table, ddl("T1"));
		table.getIndexes().add("IX");
		table.getIndexes().get(0).getColumns().add("ID");
		var operations = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertEquals(ddl("T1"), operations.get(0).getSqlText());
		assertTrue(operations.get(1).getSqlText().contains("INDEX"));
	}

	@Test
	void structuralAlterAndOwnerRemappingCannotUseOpaqueDefinitions() {
		Table before = table("T1");
		OracleTableDefinition.set(before, ddl("T1"));
		Table after = before.clone();
		after.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		Table renamed = before.clone();
		renamed.setSchemaName("OTHER");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(renamed, SqlType.CREATE));
	}

	@Test
	void corruptDefinitionsFailExplicitly() {
		Table table = table("T1");
		assertThrows(IllegalArgumentException.class, () -> OracleTableDefinition.set(table, "SELECT 1"));
		OracleTableDefinition.set(table, ddl("T1"));
		table.getSpecifics().put(OracleTableDefinition.DEFINITION, "invalid base64!");
		assertThrows(IllegalArgumentException.class, () -> OracleTableDefinition.get(table));
	}

	@Test
	void legacyDialectDoesNotQueryDbmsMetadata() {
		OracleTableReader reader = new OracleTableReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				fail("Legacy dialect must not query supplemental IOT DDL");
				return null;
			}
		};
		reader.readTableDefinitions(null, List.of(table("T1"), composite("T2")));
	}

	@Test
	void embeddedAnnotationsAreNotEmittedAgain() {
		Table table = table("T1");
		com.sqlapp.data.db.dialect.oracle.sql.OracleAnnotationUtils.setAnnotation(table, "purpose", "test");
		String definition = ddl("T1") + " ANNOTATIONS (purpose 'test')";
		OracleTableDefinition.set(table, definition);
		var operations = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		assertEquals(1, operations.size());
		assertEquals(definition, operations.get(0).getSqlText());
	}
}
