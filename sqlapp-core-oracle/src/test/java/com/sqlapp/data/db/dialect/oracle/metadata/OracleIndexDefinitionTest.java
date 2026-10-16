/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.util.OracleIndexDefinition;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleIndexDefinitionTest {
	private Index index(String name) {
		Index index = new Index(name).setSchemaName("APP").setTableName("FACT").setIndexType(IndexType.BitMap);
		index.getColumns().add("LABEL");
		index.getSpecifics().put("oracle.index.join", true);
		return index;
	}

	private String ddl(String name) {
		return "CREATE BITMAP INDEX APP." + name
				+ " ON APP.FACT (D.LABEL)\nFROM APP.FACT F, APP.DIM D WHERE F.DIM_ID=D.ID";
	}

	private Table attach(Index index) {
		Table table = new Table("FACT").setSchemaName("APP");
		table.getColumns().add("DIM_ID");
		table.getIndexes().add(index);
		return table;
	}

	private OracleIndexReader reader(int[] calls, int errorCode) {
		return new OracleIndexReader(DialectHolder.oracle26aiDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				calls[0]++;
				try {
					for (String name : List.of("IX1", "IX2")) {
						if (errorCode != 0 && name.equals("IX2"))
							throw new SQLException("metadata failure", "99999", errorCode);
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String key) {
								return switch (key) {
								case "OWNER" -> "APP";
								case "INDEX_NAME" -> name;
								case "INDEX_DEFINITION" -> ddl(name);
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
	void definitionsAreBatchedAndSurviveAttachmentAndXml() throws Exception {
		Index first = index("IX1"), second = index("IX2");
		int[] calls = { 0 };
		reader(calls, 0).readIndexDefinitions(null, List.of(first, second));
		assertEquals(1, calls[0]);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(attach(first).asXml()));
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
		assertEquals(ddl("IX1"), sql);
	}

	@Test
	void denialDoesNotKeepPartialDefinitionsAndCatalogMetadataSurvives() {
		for (int errorCode : new int[] { 1031, 31603 }) {
			Index first = index("IX1"), second = index("IX2");
			int[] calls = { 0 };
			reader(calls, errorCode).readIndexDefinitions(null, List.of(first, second));
			assertEquals(1, calls[0]);
			assertFalse(first.getSpecifics().containsKey(OracleIndexDefinition.DEFINITION));
			assertEquals("true", first.getSpecifics().get("oracle.index.join"));
			attach(first);
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(first, SqlType.CREATE));
		}
	}

	@Test
	void unrelatedMetadataFailuresPropagate() {
		assertThrows(RuntimeException.class,
				() -> reader(new int[] { 0 }, 942).readIndexDefinitions(null, List.of(index("IX1"), index("IX2"))));
	}

	@Test
	void ordinaryIndexesNeedNoSupplementalQuery() {
		Index index = index("IX1");
		index.getSpecifics().remove("oracle.index.join");
		index.setIndexType(IndexType.BTree);
		int[] calls = { 0 };
		reader(calls, 0).readIndexDefinitions(null, List.of(index));
		assertEquals(0, calls[0]);
	}

	@Test
	void changedSupplementalModelCannotReplayStaleDdl() {
		Index index = index("IX1");
		attach(index);
		OracleIndexDefinition.set(index, ddl("IX1"));
		index.getColumns().add("OTHER");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}

	@Test
	void tableCreateUsesTheSameCompleteDefinition() {
		Index index = index("IX1");
		Table table = attach(index);
		OracleIndexDefinition.set(index, ddl("IX1"));
		var sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		assertTrue(sql.stream().anyMatch(operation -> ddl("IX1").equals(operation.getSqlText())));
	}
}
