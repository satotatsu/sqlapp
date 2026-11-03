package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleExternalTableRecreationTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return values.containsKey(key.toUpperCase(java.util.Locale.ROOT));
			}

			@Override
			public String getString(String key) {
				return values.get(key.toUpperCase(java.util.Locale.ROOT));
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}
		};
	}

	@Test
	void allLocationsIncludingDuplicatesSurviveSchemaXmlInOneQuery() throws Exception {
		int[] calls = { 0 };
		var reader = new OracleExternalTableReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				try {
					for (String[] location : List.of(new String[] { "DIR A", "a,'quoted.csv" },
							new String[] { "DIR B", "b.csv" }, new String[] { "DIR B", "b.csv" }))
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "TABLE_NAME", "EXT", "TYPE_NAME",
								"ORACLE_LOADER", "DEFAULT_DIRECTORY_NAME", "DIR A", "DIRECTORY_NAME", location[0],
								"DIRECTORY_OWNER", "SYS", "LOCATION", location[1])));
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		var tables = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, calls[0]);
		assertEquals(1, tables.size());
		Schema schema = new Schema("APP");
		schema.getExternalTables().addAll(tables);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var external = restored.getExternalTables().get("EXT");
		assertNotNull(external, schema.asXml() + "\nrestored:\n" + restored.asXml());
		assertEquals("a,'quoted.csv", external.getLocation());
		assertEquals("DIR A", external.getDirectoryName());
		assertEquals("3", external.getSpecifics().get("oracle.external.locationCount"));
		assertEquals("a,'quoted.csv", external.getSpecifics().get("oracle.external.location.0.file"));
		assertEquals("DIR B", external.getSpecifics().get("oracle.external.location.1.directory"));
		assertEquals("SYS", external.getSpecifics().get("oracle.external.location.1.directoryOwner"));
		assertEquals("b.csv", external.getSpecifics().get("oracle.external.location.2.file"));
	}

	private Table table(String name) {
		Table table = new Table(name).setSchemaName("APP");
		table.getColumns().add("ID");
		table.getSpecifics().put("oracle.table.external", true);
		return table;
	}

	@Test
	void partitionAndSubpartitionLocationsSurviveXmlWithOneQuery() throws Exception {
		int[] calls = { 0 };
		var reader = new OracleExternalTableReader(DialectHolder.oracle12cDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				try {
					for (String[] part : List.of(new String[] { "part one", "" }, new String[] { "part two", "" },
							new String[] { "part two", "sub.part" })) {
						var values = new java.util.HashMap<String, String>(
								Map.of("OWNER", "APP", "TABLE_NAME", "EXT", "TYPE_NAME", "ORACLE_LOADER",
										"DIRECTORY_NAME", "DATA", "LOCATION", "same.csv", "PARTITION_NAME", part[0]));
						if (!part[1].isEmpty())
							values.put("SUBPARTITION_NAME", part[1]);
						h.handleResultSetNext(row(values));
					}
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		var version = new ProductVersionInfo().setMajorVersion(12).setMinorVersion(2);
		var objects = reader.doGetAll(null, new ParametersContext(), version);
		assertEquals(1, calls[0]);
		assertEquals(1, objects.size());
		ExternalTable restored = SchemaUtils.readXml(new java.io.StringReader(objects.get(0).asXml()));
		assertEquals("3", restored.getSpecifics().get("oracle.external.locationCount"));
		assertEquals("part one", restored.getSpecifics().get("oracle.external.location.0.partition"));
		assertEquals("part two", restored.getSpecifics().get("oracle.external.location.1.partition"));
		assertEquals("sub.part", restored.getSpecifics().get("oracle.external.location.2.subpartition"));
		assertEquals("same.csv", restored.getSpecifics().get("oracle.external.location.2.file"));
	}

	@Test
	void locationQuerySelectionUsesExactTwelveTwoBoundary() {
		var reader = new OracleExternalTableReader(DialectHolder.oracle12cDialect);
		SqlNode legacy = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(12).setMinorVersion(1));
		SqlNode modern = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(12).setMinorVersion(2));
		assertNotSame(legacy, modern);
		assertSame(legacy, reader.getSqlSqlNode(null));
		assertSame(legacy, reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(11).setMinorVersion(2)));
		assertSame(legacy, reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(12)));
		assertSame(modern, reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(19).setMinorVersion(0)));
	}

	private String ddl(String name) {
		return "CREATE TABLE APP." + name + " (ID NUMBER) ORGANIZATION EXTERNAL (TYPE ORACLE_LOADER "
				+ "DEFAULT DIRECTORY DIR_A ACCESS PARAMETERS (RECORDS DELIMITED BY NEWLINE) "
				+ "LOCATION (DIR_A:'a.csv', DIR_B:'b.csv')) REJECT LIMIT UNLIMITED";
	}

	private OracleTableReader definitions(int[] calls, int failure) {
		return new OracleTableReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				try {
					for (String name : List.of("E1", "E2")) {
						if (failure != 0 && name.equals("E2"))
							throw new SQLException("test", "99999", failure);
						h.handleResultSetNext(
								row(Map.of("OWNER", "APP", "TABLE_NAME", name, "TABLE_DEFINITION", ddl(name))));
					}
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
	}

	@Test
	void completeDefinitionsAreBatchedAndRecreatedAfterXml() throws Exception {
		Table first = table("E1"), second = table("E2");
		int[] calls = { 0 };
		definitions(calls, 0).readTableDefinitions(null, List.of(first, second, new Table("HEAP")));
		assertEquals(1, calls[0]);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(first.asXml()));
		assertEquals(ddl("E1"), DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		restored.setName("RENAMED");
		assertThrows(IllegalArgumentException.class, () -> OracleTableDefinition.get(restored));
	}

	@Test
	void deniedDefinitionsRemainCatalogOnlyAndNeverGenerateOrdinaryTable() {
		for (int code : new int[] { 1031, 31603 }) {
			Table first = table("E1");
			int[] calls = { 0 };
			definitions(calls, code).readTableDefinitions(null, List.of(first, table("E2")));
			assertEquals(1, calls[0]);
			assertFalse(first.getSpecifics().containsKey(OracleTableDefinition.DEFINITION));
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(first, SqlType.CREATE));
		}
		assertThrows(RuntimeException.class,
				() -> definitions(new int[] { 0 }, 942).readTableDefinitions(null, List.of(table("E1"), table("E2"))));
	}

	@Test
	void legacySchemaExternalTableAlsoRequiresDefinition() {
		Schema schema = new Schema("APP");
		schema.getTables().add(new Table("EXT"));
		schema.getExternalTables().add(new ExternalTable("EXT").setLocation("old.csv"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(schema.getTables().get("EXT"), SqlType.CREATE));
	}

	@Test
	void missingDefinitionDoesNotPublishTheFirstDefinition() {
		var reader = new OracleTableReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				try {
					h.handleResultSetNext(
							row(Map.of("OWNER", "APP", "TABLE_NAME", "E1", "TABLE_DEFINITION", ddl("E1"))));
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		Table first = table("E1");
		assertThrows(IllegalStateException.class, () -> reader.readTableDefinitions(null, List.of(first, table("E2"))));
		assertFalse(first.getSpecifics().containsKey(OracleTableDefinition.DEFINITION));
	}

	@Test
	void legacyDialectDoesNotUseTheModernDefinitionQuery() {
		var reader = new OracleTableReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				fail("Pre-10g must not use the complete table definition query");
				return null;
			}
		};
		reader.readTableDefinitions(null, List.of(table("E1")));
	}

	@Test
	void modernTableReadersMarkExternalTablesAndLegacyDoesNotReadTheNewColumn() throws Exception {
		for (var dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle11gR2Dialect,
				DialectHolder.oracle26aiDialect)) {
			var external = new OracleTableReader(dialect)
					.createTable(row(Map.of("OWNER", "APP", "TABLE_NAME", "EXT", "IS_EXTERNAL", "YES")));
			assertEquals("true", external.getSpecifics().get("oracle.table.external"));
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(external, SqlType.CREATE));
		}
		var legacy = new OracleTableReader(DialectHolder.defaultDialect).createTable(new ExResultSet(null) {
			@Override
			public String getString(String key) {
				assertNotEquals("IS_EXTERNAL", key);
				return key.equalsIgnoreCase("TABLE_NAME") ? "OLD" : null;
			}

			@Override
			public Object getObject(String key) {
				return null;
			}

			@Override
			public boolean contains(String key) {
				return false;
			}
		});
		assertFalse(legacy.getSpecifics().containsKey("oracle.table.external"));
	}
}
