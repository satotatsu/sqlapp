/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
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

class Db2ViewRecreationTest {
	private Map<String, String> fields() {
		var values = new HashMap<String, String>(Map.of("table_name", "V", "schema_name", "APP", "TEXT",
				"CREATE VIEW APP.V AS SELECT ID FROM T WITH LOCAL CHECK OPTION", "QUALIFIER", "BASE.SCHEMA",
				"FUNC_PATH", "\"SYSIBM\",\"Functions.Q\"", "VIEWCHECK", "L", "READONLY", "N", "VALID", "Y"));
		values.put("remarks", "view's comment");
		return values;
	}

	private List<String> create(Table table) throws Exception {
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		return factory.createSql(restored).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ")).toList();
	}

	@Test
	void nativeViewRetainsCreationContextValidityAndCommentsAfterXml() throws Exception {
		Table view = new Db2ViewReader(DialectHolder.Db2_1110Dialect).createTable(Db2MetadataParityTest.row(fields()));
		assertInstanceOf(View.class, view);
		assertTrue(view.isValid());
		assertEquals(Boolean.FALSE, view.getReadonly());
		assertEquals("L", view.getSpecifics().get("VIEWCHECK"));
		view.getColumns().add("ID", c -> c.setRemarks("column's comment"));
		var sql = create(view);
		assertEquals("SET CURRENT SCHEMA = 'BASE.SCHEMA'", sql.get(0));
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"Functions.Q\"", sql.get(1));
		assertEquals(fields().get("TEXT"), sql.get(2));
		assertEquals("COMMENT ON TABLE \"APP\".\"V\" IS 'view''s comment'", sql.get(3));
		assertEquals("COMMENT ON COLUMN \"APP\".\"V\".\"ID\" IS 'column''s comment'", sql.get(4));
	}

	@Test
	void modeledViewColumnsAndCheckOptionsAreQuotedWithoutColumnTypes() throws Exception {
		Table view = new View("V").setSchemaName("APP").setStatement("SELECT ID, VALUE FROM APP.T");
		view.getColumns().add("id.with.dot");
		view.getColumns().add("a\"b");
		view.getSpecifics().put("VIEWCHECK", "L");
		String sql = create(view).getFirst();
		assertTrue(sql.contains("\"id.with.dot\""), sql);
		assertTrue(sql.contains("\"a\"\"b\""), sql);
		assertTrue(sql.endsWith("WITH LOCAL CHECK OPTION"), sql);
		assertFalse(sql.contains("INTEGER"), sql);
		view.getSpecifics().put("VIEWCHECK", "C");
		assertTrue(create(view).getFirst().endsWith("WITH CASCADED CHECK OPTION"));
		view.getSpecifics().put("VIEWCHECK", "N");
		assertFalse(create(view).getFirst().contains("CHECK OPTION"));
		view.setName("v\"q");
		view.setSchemaName("A.B");
		assertTrue(create(view).getFirst().startsWith("CREATE VIEW \"A.B\".\"v\"\"q\""));
		SqlFactory<Table> drop = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(view,
				SqlType.DROP);
		assertEquals("DROP VIEW \"A.B\".\"v\"\"q\"", drop.createSql(view).getFirst().getSqlText());
	}

	@Test
	void viewAndMqtDefinitionsRemainOrderedSeparateOperations() throws Exception {
		for (Table object : List.of(new View("V").setSchemaName("APP"), new Mview("MQ").setSchemaName("APP"))) {
			String first = object instanceof Mview
					? "CREATE TABLE APP.MQ AS (SELECT ID FROM T) DATA INITIALLY DEFERRED REFRESH DEFERRED"
					: "CREATE VIEW APP.V AS SELECT ID FROM T";
			object.setDefinition("--#SET TERMINATOR @\n" + first + "@");
			object.getDefinition().add("COMMENT ON TABLE APP." + object.getName() + " IS 'native'@");
			object.getSpecifics().put("QUALIFIER", "BASE");
			var sql = create(object);
			assertEquals(3, sql.size());
			assertEquals(first, sql.get(1));
			assertTrue(sql.get(2).startsWith("COMMENT ON TABLE"));
		}
	}

	@Test
	void invalidBodiesChecksAndImplicitReplacementFailClearly() throws Exception {
		Table view = new View("V").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> create(view));
		view.setStatement("SELECT 1 FROM SYSIBM.SYSDUMMY1");
		view.getSpecifics().put("VIEWCHECK", "?");
		assertThrows(IllegalArgumentException.class, () -> create(view));
		view.getSpecifics().put("VIEWCHECK", "L");
		view.setReadonly(true);
		assertThrows(IllegalArgumentException.class, () -> create(view));
		view.setReadonly(false);
		view.getSpecifics().put("FUNC_PATH", "\"unterminated");
		assertThrows(IllegalArgumentException.class, () -> create(view));
		view.getSpecifics().clear();
		Table changed = view.clone().setStatement("SELECT 2 FROM SYSIBM.SYSDUMMY1");
		SqlFactory<Table> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(changed,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(view.diff(changed)));
		Schema before = new Schema("APP"), after = new Schema("APP");
		before.getViews().add((View) view);
		after.getViews().add((View) changed);
		SqlFactory<Schema> schemaAlter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> schemaAlter.createDiffSql(before.diff(after)));
		view.getSpecifics().put("DB2_VIEW_TYPE", "W");
		assertThrows(IllegalArgumentException.class, () -> create(view));
	}

	@Test
	void twentyViewsUseOneQueryAndOldVersionSqlIsSeparate() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ViewReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(sql.toString().contains("t.REMARKS"));
				for (int i = 0; i < 20; i++)
					try {
						var values = fields();
						values.put("table_name", "V" + i);
						handler.handleResultSetNext(Db2MetadataParityTest.row(values));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		var views = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, views.size());
		assertTrue(views.stream().allMatch(v -> "BASE.SCHEMA".equals(v.getSpecifics().get("QUALIFIER"))));
		assertFalse(new Db2ViewReader(DialectHolder.Db2_1050Dialect).getSqlNode(new ProductVersionInfo()).toString()
				.contains("t.REMARKS"));
	}
}
