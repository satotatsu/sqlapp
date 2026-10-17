/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.sql.OracleCreateViewFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class OracleViewRecreationTest {
	@Test
	void catalogRestrictionsAreSeparatedWithoutChangingLiteralsOrComments() {
		for (boolean readOnly : new boolean[] { false, true }) {
			String restriction = readOnly ? "WITH READ ONLY" : "WITH CHECK OPTION";
			String query = "SELECT q'[a' WITH READ ONLY]' AS V, 'WITH CHECK OPTION' AS X FROM T";
			assertEquals(query, OracleViewReader.withoutRestriction(query + " " + restriction, readOnly));
			assertEquals(query + " /* WITH READ ONLY */",
					OracleViewReader.withoutRestriction(query + " " + restriction + " /* WITH READ ONLY */", readOnly));
			assertEquals(query + " -- " + restriction,
					OracleViewReader.withoutRestriction(query + " -- " + restriction, readOnly));
		}
	}

	@Test
	void removedCommentsAreExplicitlyCleared() {
		View before = new View("V");
		before.setStatement("SELECT ID FROM T");
		before.setRemarks("old");
		before.getColumns().add("ID");
		before.getColumns().get(0).setRemarks("old column");
		View after = (View) before.clone();
		after.setRemarks(null);
		after.getColumns().get(0).setRemarks(null);
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(2, operations.size());
		assertEquals("COMMENT ON TABLE V IS NULL", operations.get(0).getSqlText());
		assertEquals("COMMENT ON COLUMN V.ID IS NULL", operations.get(1).getSqlText());
	}

	@Test
	void ordinaryViewReplacementKeepsColumnsAndRestrictions() {
		View before = new View("V");
		before.setStatement("SELECT ID FROM T");
		before.getColumns().add("ID");
		View after = (View) before.clone();
		after.setStatement("SELECT OTHER_ID FROM T");
		after.getSpecifics().put(OracleCreateViewFactory.READ_ONLY_CONSTRAINT, "R");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(SqlType.ALTER, operations.get(0).getSqlType());
		assertTrue(operations.get(0).getSqlText().startsWith("CREATE OR REPLACE VIEW"));
		assertTrue(operations.get(0).getSqlText().contains("WITH READ ONLY CONSTRAINT R"));
		assertTrue(registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(after.diff(after.clone())).isEmpty());
	}

	@Test
	void viewReplacementRejectsIdentityChanges() {
		View before = new View("V");
		before.setStatement("SELECT ID FROM T");
		View after = (View) before.clone();
		after.setName("OTHER");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		View opaque = new View("V");
		opaque.setStatement("SELECT ID FROM T");
		opaque.setDefinition("CREATE VIEW V AS SELECT ID FROM T");
		View edited = (View) opaque.clone();
		edited.setStatement("SELECT OTHER_ID FROM T");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(edited, SqlType.ALTER).createDiffSql(opaque.diff(edited)));
	}

	@Test
	void typeMetadataCannotChangeWithoutTheQuery() {
		View before = new View("V");
		before.setStatement("SELECT ID FROM T");
		before.getColumns().add("ID");
		View after = (View) before.clone();
		after.getColumns().get(0).setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	private ExResultSet row(String name, boolean readOnly) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return switch (key) {
				case "VIEW_NAME" -> name;
				case "OWNER" -> "APP";
				case "TEXT" -> "SELECT ID FROM T " + (readOnly ? "WITH READ ONLY" : "WITH CHECK OPTION");
				case "STATUS" -> "VALID";
				case "ORACLE_CHECK_CONSTRAINT" -> readOnly ? null : "check name";
				case "ORACLE_READ_ONLY_CONSTRAINT" -> readOnly ? "read name" : null;
				default -> null;
				};
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}

			@Override
			public java.sql.Timestamp getTimestamp(String key) {
				return null;
			}
		};
	}

	@Test
	void viewRestrictionsAreReadInTheExistingBulkQuery() {
		int[] calls = { 0 };
		OracleViewReader reader = new OracleViewReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				calls[0]++;
				try {
					handler.handleResultSetNext(row("V1", false));
					handler.handleResultSetNext(row("V2", true));
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		List<Table> views = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, calls[0]);
		assertEquals(2, views.size());
		assertEquals("check name", views.get(0).getSpecifics().get(OracleCreateViewFactory.CHECK_CONSTRAINT));
		assertEquals("read name", views.get(1).getSpecifics().get(OracleCreateViewFactory.READ_ONLY_CONSTRAINT));
	}

	@Test
	void readerRestrictionsAndColumnNamesRecreateAfterXml() throws Exception {
		for (boolean readOnly : new boolean[] { false, true }) {
			View view = new OracleViewReader(DialectHolder.oracle10gDialect).createTable(row("V", readOnly));
			view.getColumns().add("renamed column");
			View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect,
					DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ")
						.replace("( ", "(");
				assertTrue(ddl.contains("APP.V (\"renamed column\")"), ddl);
				assertTrue(ddl.contains(readOnly ? "WITH READ ONLY CONSTRAINT \"read name\""
						: "WITH CHECK OPTION CONSTRAINT \"check name\""), ddl);
				assertEquals(1, ddl.split(readOnly ? "WITH READ ONLY" : "WITH CHECK OPTION", -1).length - 1, ddl);
			}
		}
	}

	@Test
	void tableAndColumnCommentsUseOracleCommentSyntax() {
		View view = new View("V");
		view.setSchemaName("APP");
		view.setStatement("SELECT ID FROM T");
		view.setRemarks("owner's view");
		view.getColumns().add("ID");
		view.getColumns().get(0).setRemarks("identifier");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(view, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertEquals("COMMENT ON TABLE APP.V IS 'owner''s view'", operations.get(1).getSqlText());
		assertEquals("COMMENT ON COLUMN APP.V.ID IS 'identifier'", operations.get(2).getSqlText());
	}

	@Test
	void incompleteAndConflictingRepresentationsAreRejected() {
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		View view = new View("V");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setStatement("SELECT ID FROM T");
		view.getSpecifics().put(OracleCreateViewFactory.CHECK_CONSTRAINT, "C");
		view.getSpecifics().put(OracleCreateViewFactory.READ_ONLY_CONSTRAINT, "R");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.getSpecifics().remove(OracleCreateViewFactory.READ_ONLY_CONSTRAINT);
		view.setDefinition("CREATE VIEW V AS SELECT ID FROM T");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.getSpecifics().clear();
		assertEquals(view.getDefinition().get(0), registry.createSql(view, SqlType.CREATE).get(0).getSqlText());
	}
}
