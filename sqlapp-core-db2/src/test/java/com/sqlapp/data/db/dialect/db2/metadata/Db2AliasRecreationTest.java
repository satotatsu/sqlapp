/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2AliasRecreationTest {
	private Map<String, String> fields(String kind, boolean publicAlias) {
		return Map.of("object_name", "A\".Q", "schema_name", publicAlias ? "SYSPUBLIC" : "S.Q", "base_schema", "T\".Q",
				"base_object", "B.Q", "alias_type", kind, "remarks", "alias's comment");
	}

	private List<String> sql(Synonym alias, SqlType type) throws Exception {
		Synonym restored = SchemaUtils.readXml(new StringReader(alias.asXml()));
		SqlFactory<Synonym> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				type);
		return factory.createSql(restored).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void allPrivateAndPublicKindsRetainLiteralNamesTargetsAndCommentsAfterXml() throws Exception {
		for (String kind : List.of("TABLE", "SEQUENCE", "MODULE"))
			for (boolean publicAlias : List.of(false, true)) {
				Synonym alias = new Db2SynonymReader(DialectHolder.Db2_1110Dialect)
						.createSynonym(Db2MetadataParityTest.row(fields(kind, publicAlias)));
				String designator = (publicAlias ? "PUBLIC ALIAS \"SYSPUBLIC\"" : "ALIAS \"S.Q\"") + ".\"A\"\".Q\"";
				assertEquals(
						List.of("CREATE " + designator + " FOR " + kind + " \"T\"\".Q\".\"B.Q\"",
								"COMMENT ON " + designator + " FOR " + kind + " IS 'alias''s comment'"),
						sql(alias, SqlType.CREATE));
				assertEquals(List.of("DROP " + designator + " FOR " + kind), sql(alias, SqlType.DROP));
				assertEquals(alias, alias.clone());
			}
	}

	@Test
	void fullDefinitionScriptsRemainAuthoritativeAndDoNotRequireDuplicateTargets() throws Exception {
		Synonym alias = new Synonym("A").setSchemaName("APP");
		String ddl = "CREATE ALIAS APP.A\n FOR TABLE TARGET.T";
		alias.setDefinition(ddl);
		assertEquals(List.of(ddl), sql(alias, SqlType.CREATE));
		alias.setDefinition("--#SET TERMINATOR @\n" + ddl + "@\nCOMMENT ON ALIAS APP.A FOR TABLE IS 'native'@");
		assertEquals(List.of(ddl, "COMMENT ON ALIAS APP.A FOR TABLE IS 'native'"), sql(alias, SqlType.CREATE));
	}

	@Test
	void incompleteTargetsRemoteLinksAndImplicitReplacementsFailClearly() throws Exception {
		Synonym alias = new Synonym("A").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> sql(alias, SqlType.CREATE));
		alias.setObjectName("B").setDbLinkName("REMOTE");
		assertThrows(IllegalArgumentException.class, () -> sql(alias, SqlType.CREATE));
		alias.setDbLinkName(null);
		alias.getSpecifics().put("ALIAS_TYPE", "?");
		assertThrows(IllegalArgumentException.class, () -> sql(alias, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(alias, SqlType.DROP));
		alias.getSpecifics().clear();
		Synonym changed = alias.clone().setObjectName("C");
		SqlFactory<Synonym> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(changed,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(alias.diff(changed)));
		Schema before = new Schema("APP"), after = new Schema("APP");
		before.getSynonyms().add(alias);
		after.getSynonyms().add(changed);
		SqlFactory<Schema> schemaAlter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> schemaAlter.createDiffSql(before.diff(after)));
	}

	@Test
	void selectedDialectResolvesCatalogWithoutVersionAndUsesOneQueryForAllKinds() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2SynonymReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				assertTrue(node.toString().contains("SYSCAT.MODULES"));
				assertTrue(node.toString().contains("BASE_SEQNAME"));
				for (String kind : List.of("TABLE", "SEQUENCE", "MODULE"))
					for (int i = 0; i < 10; i++)
						try {
							handler.handleResultSetNext(Db2MetadataParityTest.row(fields(kind, false)));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
				return null;
			}
		};
		assertEquals(30, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(1, count.get());
		var old = new Db2SynonymReader(DialectHolder.Db2_950Dialect);
		assertFalse(old.getSqlNode(new ProductVersionInfo()).toString().contains("SYSCAT.MODULES"));
		assertTrue(old.getSqlNode(new ProductVersionInfo().setMajorVersion(9).setMinorVersion(7)).toString()
				.contains("SYSCAT.MODULES"));
	}
}
