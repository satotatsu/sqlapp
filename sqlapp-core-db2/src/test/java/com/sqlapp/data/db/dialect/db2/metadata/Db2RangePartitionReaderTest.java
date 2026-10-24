/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2RangePartitionReaderTest {
	@Test
	void boundariesAndQuotedKeysRetainInclusivityNullOrderingAndSpaces() throws Exception {
		var reader = new Db2_970TableReader(DialectHolder.Db2_970Dialect);
		var table = new Table("T");
		table.getColumns().add("Key.Name");
		reader.readPartitionExrepssion(
				Db2MetadataParityTest.row(Map.of("DATAPARTITIONEXPRESSION", "\"Key.Name\"", "NULLSFIRST", "Y")), table);
		assertEquals("Key.Name", table.getPartitioning().getPartitioningColumns().getFirst().getName());
		assertEquals(NullsOrder.NullsFirst,
				table.getPartitioning().getPartitioningColumns().getFirst().getNullsOrder());
		var values = Map.of("DATAPARTITIONNAME", "P", "LOWVALUE", "1", "HIGHVALUE", "10", "LOWINCLUSIVE", "N",
				"HIGHINCLUSIVE", "Y", "TABLE_SPACE", "DATA", "INDEX_TABLE_SPACE", "IDX", "LOB_TABLE_SPACE", "LOB");
		ExResultSet rs = new ExResultSet(null) {
			@Override
			public String getString(String key) throws SQLException {
				if ("PARTITION_EXPRESSION".equals(key))
					throw new SQLException("Column is absent in DATAPARTITIONS");
				return values.get(key);
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}
		};
		var partition = reader.readPartition(rs, table);
		assertFalse(partition.isLowValueInclusive());
		assertTrue(partition.isHighValueInclusive());
		assertEquals("IDX", partition.getIndexTableSpaceName());
		assertEquals("LOB", partition.getLobTableSpaceName());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals(table, restored);
		assertEquals(table, table.clone());
	}

	@Test
	void rawExpressionsAreNotTreatedAsQuotedColumnNamesAndInvalidFlagsFail() throws Exception {
		var reader = new Db2TableReader(DialectHolder.Db2_1110Dialect);
		var table = new Table("T");
		reader.readPartitionExrepssion(
				Db2MetadataParityTest.row(Map.of("DATAPARTITIONEXPRESSION", "YEAR(\"CREATED\")", "NULLSFIRST", "N")),
				table);
		assertEquals(List.of("YEAR(\"CREATED\")"),
				table.getPartitioning().getPartitioningColumns().getFirst().getStatement());
		assertThrows(SQLException.class, () -> reader.readPartitionExrepssion(
				Db2MetadataParityTest.row(Map.of("DATAPARTITIONEXPRESSION", "C", "NULLSFIRST", "X")), table));
		assertThrows(SQLException.class, () -> reader.readPartition(
				Db2MetadataParityTest.row(Map.of("DATAPARTITIONNAME", "P", "LOWINCLUSIVE", "X")), table));
	}

	@Test
	void twentyTablesUseTwoQueriesAndPre91DoesNotReadRangeCatalogs() {
		var tables = com.sqlapp.util.CommonUtils.<String, String, Table>doubleKeyMap();
		for (int i = 0; i < 20; i++) {
			var table = new Table("T" + i).setSchemaName("APP");
			table.getColumns().add("K");
			tables.put("APP", table.getName(), table);
		}
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2TableReader(DialectHolder.Db2_910Dialect) {
			@Override
			protected ParametersContext defaultParametersContext(Connection connection) {
				return new ParametersContext();
			}

			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext context,
					ResultSetNextHandler h) {
				int query = count.incrementAndGet();
				for (int i = 0; i < 20; i++)
					try {
						Map<String, String> row = query == 1
								? Map.of("schema_name", "APP", "table_name", "T" + i, "DATAPARTITIONEXPRESSION", "K",
										"NULLSFIRST", "Y")
								: Map.of("schema_name", "APP", "table_name", "T" + i, "DATAPARTITIONNAME", "P",
										"LOWVALUE", "0", "HIGHVALUE", "10", "LOWINCLUSIVE", "Y", "HIGHINCLUSIVE", "N");
						h.handleResultSetNext(Db2MetadataParityTest.row(row));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		reader.setPartitionExpression(null, tables);
		reader.setPartitioning(null, tables);
		assertEquals(2, count.get());
		assertEquals(1, tables.get("APP", "T19").getPartitioning().getPartitions().size());
		var legacy = new Db2TableReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				throw new AssertionError("Legacy Db2 has no range catalogs");
			}
		};
		legacy.setPartitionExpression(null, tables);
		legacy.setPartitioning(null, tables);
	}
}
