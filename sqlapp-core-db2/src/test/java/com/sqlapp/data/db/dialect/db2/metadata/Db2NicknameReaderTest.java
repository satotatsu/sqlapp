/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2NicknameReaderTest {
	private com.sqlapp.jdbc.ExResultSet tableRow(Map<String, String> values) {
		return new com.sqlapp.jdbc.ExResultSet(null) {
			@Override
			public String getString(String label) {
				return values.get(label);
			}

			@Override
			public Object getObject(String label) {
				return values.get(label);
			}

			@Override
			public java.sql.Timestamp getTimestamp(String label) {
				return null;
			}

			@Override
			public boolean contains(String label) {
				return false;
			}
		};
	}

	@Test
	void tableReadSeparatesNicknamesFromLocalTablesAndPreservesLiteralIdentity() throws Exception {
		var reader = new Db2TableReader(DialectHolder.Db2_1215Dialect);
		String query = reader.getSqlNode(new ProductVersionInfo()).toString();
		assertTrue(query.contains("LEFT JOIN SYSCAT.NICKNAMES"));
		assertTrue(query.contains("'N'"));
		assertFalse(new Db2TableReader(DialectHolder.defaultDialect).getSqlNode(new ProductVersionInfo()).toString()
				.contains("SYSCAT.NICKNAMES"));
		assertTrue(new Db2TableReader(DialectHolder.Db2_1110Dialect).getSqlNode(new ProductVersionInfo()).toString()
				.contains("SYSCAT.NICKNAMES"));
		Table nickname = reader.createTable(tableRow(Map.of("schema_name", "A.B", "table_name", "N.N", "TYPE", "N",
				"nickname_server", "S.S", "nickname_remote_schema", "remote.schema", "nickname_remote_table",
				"remote.table", "nickname_wrapper_type", "R", "nickname_caching", "N")));
		assertEquals("remote.schema", Db2Nickname.get(nickname).remoteSchema());
		assertEquals("S.S", Db2Nickname.get(nickname).serverName());
		assertFalse(Db2Nickname.isNickname(reader.createTable(tableRow(Map.of("table_name", "T", "TYPE", "T")))));
	}

	@Test
	void twentyNicknamesAcrossSchemasUseOneAdditionalQueryAndSkipOrdinaryTables() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2TableReader(DialectHolder.Db2_1215Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("FROM SYSCAT.COLOPTIONS"));
				assertEquals("retained", context.get("custom"));
				for (int i = 0; i < 20; i++) {
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("schema_name", "S" + i,
								"table_name", "SAME", "option_name", "CUSTOM", "SETTING", " x's ")));
						handler.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("schema_name", "S" + i, "table_name", "SAME", "column_name", "LOCAL",
										"option_name", "REMOTE_COLUMN_NAME", "SETTING", "REMOTE")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		List<Table> tables = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Table table = new Table("SAME").setSchemaName("S" + i);
			Db2Nickname.set(table, new Db2Nickname.Snapshot(1, "SERVER", null, "REMOTE", "R", "N", Map.of(), Map.of()));
			tables.add(table);
		}
		ParametersContext context = new ParametersContext();
		context.put("custom", "retained");
		reader.setNicknameOptions(null, context, tables);
		assertEquals(1, queries.get());
		for (var table : tables) {
			assertEquals(" x's ", Db2Nickname.get(table).options().get("CUSTOM"));
			assertEquals("REMOTE", Db2Nickname.get(table).columnOptions().get("LOCAL").get("REMOTE_COLUMN_NAME"));
		}
		reader.setNicknameOptions(null, context, List.of(new Table("LOCAL")));
		assertEquals(1, queries.get());
	}
}
