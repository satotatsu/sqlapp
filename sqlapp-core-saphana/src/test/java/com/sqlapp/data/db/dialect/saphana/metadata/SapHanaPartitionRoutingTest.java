/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaPartitionRoutingTest {
	@Test
	void fetchesOneCompleteDefinitionPerThreeLevelTableRatherThanPerPartition() {
		var calls = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected Table createTable(ExResultSet rs) {
				return new Table("T").setSchemaName("APP");
			}

			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				int call = calls.incrementAndGet();
				try {
					for (int i = 0; i < (call == 2 ? 8 : 1); i++)
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public Object getObject(String name) {
								return name.equals("LEVEL_3_PARTITION") ? 1 : null;
							}

							@Override
							public String getString(String name) {
								return switch (name.toUpperCase(java.util.Locale.ROOT)) {
								case "SCHEMA_NAME" -> "APP";
								case "TABLE_NAME" -> "T";
								case "OBJECT_CREATION_STATEMENT" -> "CREATE COLUMN TABLE APP.T (A INTEGER)";
								default -> null;
								};
							}
						});
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var table = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).getFirst();
		assertEquals(3, calls.get());
		assertNull(table.getPartitioning());
		assertFalse(table.getDefinition().isEmpty());
		assertEquals("DEFINITION", table.getSpecifics().get("PARTITIONING_REPRESENTATION"));
	}

	@Test
	void preservesLegacyDynamicPoliciesWithoutOverridingExplicitFalse() {
		var part = new com.sqlapp.data.schemas.Partition("OTHER");
		SapHanaTableReader.inferLegacyDynamic(part);
		assertNull(part.getSpecifics().get("IS_DYNAMIC"));
		part.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", 5000000000L);
		SapHanaTableReader.inferLegacyDynamic(part);
		assertEquals("TRUE", part.getSpecifics().get("IS_DYNAMIC"));
		part.getSpecifics().put("IS_DYNAMIC", "FALSE");
		SapHanaTableReader.inferLegacyDynamic(part);
		assertEquals("FALSE", part.getSpecifics().get("IS_DYNAMIC"));
		part.getSpecifics().remove("IS_DYNAMIC");
		part.setLowValue("0").setHighValue("10");
		SapHanaTableReader.inferLegacyDynamic(part);
		assertNull(part.getSpecifics().get("IS_DYNAMIC"));
		part.setLowValue((String) null).setHighValue((String) null);
		part.getStatistics().clear();
		part.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", "2 YEAR");
		SapHanaTableReader.inferLegacyDynamic(part);
		assertEquals("TRUE", part.getSpecifics().get("IS_DYNAMIC"));
	}

	@Test
	void rejectsThirdLevelDeclarationsAndLeavesWithoutFlattening() throws Exception {
		for (var entry : java.util.Map.<String, Object>of("LEVEL_3_TYPE", "RANGE HETEROGENEOUS", "LEVEL_3_EXPRESSION",
				"C", "LEVEL_3_COUNT", 2L, "LEVEL_3_PARTITION", 1).entrySet()) {
			var row = new ExResultSet(null) {
				@Override
				public Object getObject(String name) {
					return name.equals(entry.getKey()) ? entry.getValue() : null;
				}
			};
			var failure = assertThrows(SQLException.class,
					() -> SapHanaTableReader.validatePartitionDepth(row, "APP", "T"));
			assertTrue(failure.getMessage().contains("APP.T"));
			assertTrue(failure.getMessage().contains("original CREATE TABLE DDL"));
		}
		for (boolean absent : new boolean[] { true, false }) {
			SapHanaTableReader.validatePartitionDepth(new ExResultSet(null) {
				@Override
				public Object getObject(String name) {
					return absent ? null : name.endsWith("COUNT") || name.endsWith("PARTITION") ? 0 : "";
				}
			}, "APP", "T");
		}
	}

	@Test
	void retainsDistinctParentRangesThroughXmlWithTwoQueries() throws Exception {
		var count = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected Table createTable(ExResultSet rs) {
				return new Table("T").setSchemaName("APP");
			}

			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				int query = count.incrementAndGet();
				try {
					for (int p = query == 1 ? 1 : 0; p <= (query == 1 ? 1 : 2); p++) {
						final int parent = p;
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String name) {
								return switch (name.toUpperCase(java.util.Locale.ROOT)) {
								case "SCHEMA_NAME" -> "APP";
								case "TABLE_NAME" -> "T";
								case "PART_ID" -> parent == 0 ? null : Integer.toString(parent);
								case "LEVEL_1_EXPRESSION" -> "A";
								case "LEVEL_2_EXPRESSION" -> "B";
								case "LEVEL_1_TYPE", "LEVEL_2_TYPE" -> "RANGE HETEROGENEOUS";
								case "LEVEL_1_RANGE_MIN_VALUE", "LEVEL_2_RANGE_MIN_VALUE" -> "0";
								case "LEVEL_1_RANGE_MAX_VALUE" -> Integer.toString(parent * 10);
								case "LEVEL_2_RANGE_MAX_VALUE" -> Integer.toString(parent * 100);
								default -> null;
								};
							}

							@Override
							public int getInt(String name) {
								return switch (name.toUpperCase(java.util.Locale.ROOT)) {
								case "LEVEL_1_PARTITION" -> parent;
								case "LEVEL_2_PARTITION" -> parent == 0 ? 0 : 1;
								default -> 2;
								};
							}

							@Override
							public boolean wasNull() {
								return false;
							}

							@Override
							public Object getObject(String name) {
								if (name.equals("DYNAMIC_RANGE_THRESHOLD"))
									return 5000000000L;
								if (name.equals("IS_DYNAMIC"))
									return "FALSE";
								if (name.equals("INSERT"))
									return Boolean.TRUE;
								if (name.equals("SUBTYPE"))
									return "SUB";
								return name.toUpperCase(java.util.Locale.ROOT).matches("LEVEL_[12]_(COUNT|PARTITION)")
										? getInt(name)
										: null;
							}
						});
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		var table = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).getFirst();
		assertEquals(2, count.get());
		assertNotEquals(table.getPartitioning().getPartitions().getFirst().getName(),
				table.getPartitioning().getPartitions().get(1).getName());
		var restored = (Table) com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals("10", restored.getPartitioning().getPartitions().getFirst().getHighValue());
		assertEquals("20", restored.getPartitioning().getPartitions().get(1).getHighValue());
		assertEquals("100",
				restored.getPartitioning().getPartitions().getFirst().getSubPartitions().getFirst().getHighValue());
		var leaf = restored.getPartitioning().getPartitions().getFirst().getSubPartitions().getFirst();
		assertEquals("5000000000", leaf.getStatistics().get("DYNAMIC_RANGE_THRESHOLD"));
		assertEquals("FALSE", leaf.getSpecifics().get("IS_DYNAMIC"));
		assertEquals("true", leaf.getSpecifics().get("INSERT"));
		assertEquals("SUB", leaf.getSpecifics().get("SUB_TYPE"));
	}

	@Test
	void parsesQuotedPartitionColumnsAndEmptyRoundRobinExpressions() {
		assertTrue(SapHanaTableReader.partitionColumns(null).isEmpty());
		assertEquals(java.util.List.of("A,B", "A\"B", "ID"),
				SapHanaTableReader.partitionColumns("\"A,B\", \"A\"\"B\", ID"));
		assertEquals(java.util.List.of("YEAR(\"D\")"), SapHanaTableReader.partitionColumns("YEAR(\"D\")"));
		assertEquals(java.util.List.of("\"YEAR(D)\""), SapHanaTableReader.partitionColumns("\"YEAR(D)\""));
		assertThrows(IllegalArgumentException.class, () -> SapHanaTableReader.partitionColumns("\"unfinished"));
	}

	@Test
	void skipsEmptySelections() {
		var count = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).isEmpty());
		assertEquals(1, count.get());
	}

	@Test
	void skipsSchemaTableCrossProductRows() {
		var count = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected Table createTable(ExResultSet rs) throws SQLException {
				return new Table(rs.getString("table_name")).setSchemaName(rs.getString("schema_name"));
			}

			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				int query = count.incrementAndGet();
				try {
					for (String schema : query == 1 ? java.util.List.of("A", "B") : java.util.List.of("A")) {
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String name) {
								return name.equals("schema_name") ? schema
										: query == 1 && schema.equals("A") ? "T1" : "T2";
							}
						});
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		assertEquals(2, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(2, count.get());
	}
}
