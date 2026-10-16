/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleDomainIndexRecreationTest {
	private Table read(String type) {
		var values = new java.util.HashMap<>(Map.of("OWNER", "APP", "INDEX_NAME", "IX", "TABLE_NAME", "T",
				"COLUMN_NAME", "DOC", "INDEX_TYPE", type, "STATUS", "VALID", "COMPRESSION", "DISABLED"));
		values.putAll(Map.of("ITYP_OWNER", "Text Owner", "ITYP_NAME", "Context Type", "PARAMETERS",
				"lexer O'Reilly\nFILTER OFF", "DOMIDX_STATUS", "VALID", "DOMIDX_OPSTATUS", "VALID"));
		int[] count = { 0 };
		OracleIndexReader reader = new OracleIndexReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				count[0]++;
				try {
					handler.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String key) {
							return values.get(key.toUpperCase(java.util.Locale.ROOT));
						}

						@Override
						public Object getObject(String key) {
							return getString(key);
						}

						@Override
						public Long getLongValue(String key) {
							return null;
						}
					});
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		var indexes = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, count[0]);
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("DOC").setDataType(DataType.CLOB));
		table.getIndexes().add(indexes.get(0));
		return table;
	}

	private String create(Table table) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(table.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void catalogDomainTypeAndParametersSurviveXmlAndGeneration() throws Exception {
		Table table = read("DOMAIN");
		assertEquals(IndexType.Domain, table.getIndexes().get(0).getIndexType());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = create(restored);
		assertTrue(sql.contains("DOC"), sql);
		assertTrue(sql.contains("INDEXTYPE IS \"Text Owner\".\"Context Type\""), sql);
		assertTrue(sql.contains("PARAMETERS ('lexer O''Reilly\nFILTER OFF')"), sql);
		assertFalse(sql.contains("COMPRESS"), sql);
	}

	@Test
	void parameterOnlyTableDiffUsesAlterWithoutDroppingTheIndex() {
		Table before = read("DOMAIN"), after = before.clone();
		after.getIndexes().get(0).getSpecifics().put("PARAMETERS", "SYNC (MANUAL)");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		var sql = factory.createDiffSql(before.diff(after));
		assertEquals(1, sql.size());
		assertEquals("ALTER INDEX APP.IX PARAMETERS ('SYNC (MANUAL)')", sql.get(0).getSqlText());
	}

	@Test
	void rebuildDoesNotReplayCreationParameters() {
		Index index = read("DOMAIN").getIndexes().get(0);
		for (var dialect : java.util.List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle12cDialect,
				DialectHolder.oracle26aiDialect))
			assertEquals("ALTER INDEX APP.IX REBUILD",
					dialect.createSqlFactoryRegistry().createSql(index, SqlType.REBUILD).get(0).getSqlText());
	}

	@Test
	void incompleteAndInvalidDomainMetadataFailClearly() {
		Table table = read("DOMAIN");
		table.getIndexes().get(0).getSpecifics().remove("ITYP_NAME");
		assertThrows(IllegalArgumentException.class, () -> create(table));
		Table failed = read("DOMAIN");
		failed.getIndexes().get(0).getSpecifics().put("DOMIDX_OPSTATUS", "FAILED");
		assertThrows(IllegalArgumentException.class, () -> create(failed));
	}

	@Test
	void unsupportedDomainOptionsAreNotSilentlyDiscarded() {
		Table table = read("DOMAIN");
		table.getIndexes().get(0).setPartitioning(new Partitioning());
		assertThrows(IllegalArgumentException.class, () -> create(table));
		Table moved = read("DOMAIN");
		moved.getIndexes().get(0).setTableSpaceName("USERS");
		assertThrows(IllegalArgumentException.class, () -> create(moved));
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(read("DOMAIN").getIndexes().get(0), SqlType.REBUILD));
	}

	@Test
	void reverseIndexMetadataIsNotRecreatedAsAnOrdinaryBTree() throws Exception {
		Table table = read("NORMAL/REV");
		assertEquals(IndexType.BTree, table.getIndexes().get(0).getIndexType());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertTrue(create(restored).endsWith("REVERSE"));
	}

	@Test
	void joinAndUnknownIndexTypesRequireDedicatedDefinitions() {
		Table join = read("BITMAP");
		join.getIndexes().get(0).getSpecifics().put("oracle.index.join", true);
		assertThrows(IllegalArgumentException.class, () -> create(join));
		Table unknown = read("UNSUPPORTED_VENDOR_INDEX");
		assertThrows(IllegalArgumentException.class, () -> create(unknown));
	}

	@Test
	void parametersKeepOuterQuotesAndTrailingWhitespace() {
		Table table = read("DOMAIN");
		table.getIndexes().get(0).getSpecifics().put("PARAMETERS", "'quoted' ");
		assertTrue(create(table).endsWith("PARAMETERS ('''quoted'' ')"));
	}

	@Test
	void invalidEncodedParametersAndMissingAlterTargetFailClearly() {
		Table table = read("DOMAIN");
		table.getIndexes().get(0).getSpecifics().put("oracle.index.domain.parametersBase64", "invalid!");
		assertThrows(IllegalArgumentException.class, () -> create(table));
		Table before = read("DOMAIN"), after = before.clone();
		after.getIndexes().get(0).getSpecifics().remove("oracle.index.domain.parametersBase64");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(after)));
	}
}
