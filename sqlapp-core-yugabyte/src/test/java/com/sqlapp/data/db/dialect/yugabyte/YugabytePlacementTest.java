/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableSpace;

class YugabytePlacementTest {
	private Table table() {
		Table table = new Table("items").setSchemaName("app");
		table.getColumns().add("tenant", c -> c.setDataType(DataType.INT));
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().toArray());
		return table;
	}

	private String sql(Dialect dialect, Object object) {
		return dialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE).stream().map(op -> op.getSqlText())
				.collect(java.util.stream.Collectors.joining(";"));
	}

	@Test
	void preservesGroupedHashKeysAndQuotedTablespaceThroughXml() throws Exception {
		for (Dialect dialect : java.util.List.of(new Yugabyte11(), new Yugabyte15())) {
			Table table = table().setTableSpaceName("space name");
			table.getSpecifics().put("YSQL_COLOCATION", "false");
			table.getSpecifics().put("YSQL_SPLIT_INTO", "3");
			table.getConstraints().getPrimaryKeyConstraint().getSpecifics().put("YSQL_HASH_COLUMNS", "2");
			Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
			String sql = sql(dialect, restored);
			assertTrue(sql.contains("HASH"), sql);
			assertTrue(sql.contains("SPLIT INTO 3 TABLETS"), sql);
			assertTrue(sql.contains("TABLESPACE \"space name\""), sql);
			assertTrue(sql.contains("COLOCATION = false"), sql);
		}
	}

	@Test
	void quotesPlacementAsLiteralAndPreservesOwner() throws Exception {
		TableSpace space = new TableSpace("space name");
		space.setOwnerName("owner name");
		space.getSpecifics().put("YSQL_REPLICA_PLACEMENT", "{\"region\":\"a'b\"}");
		TableSpace restored = SchemaUtils.readXml(new StringReader(space.asXml()));
		String sql = sql(new Yugabyte15(), restored);
		assertTrue(sql.contains("OWNER \"owner name\""), sql);
		assertTrue(sql.contains("a''b"), sql);
		assertFalse(sql.contains("LOCATION"), sql);
	}

	@Test
	void rejectsInvalidOrContradictoryPlacementSettings() {
		Table table = table();
		table.getSpecifics().put("YSQL_COLOCATION", "maybe");
		assertThrows(IllegalArgumentException.class, () -> sql(new Yugabyte15(), table));
		table.getSpecifics().put("YSQL_COLOCATION", "true");
		table.getSpecifics().put("YSQL_SPLIT_INTO", "2");
		assertThrows(IllegalArgumentException.class, () -> sql(new Yugabyte15(), table));
		table.getSpecifics().put("YSQL_COLOCATION", "false");
		table.getSpecifics().put("YSQL_SPLIT_INTO", "0");
		assertThrows(IllegalArgumentException.class, () -> sql(new Yugabyte11(), table));
		table.getSpecifics().put("YSQL_SPLIT_INTO", "2");
		table.getSpecifics().put("YSQL_SPLIT_AT_BASE64", "invalid");
		assertThrows(IllegalArgumentException.class, () -> sql(new Yugabyte11(), table));
	}

	@Test
	void requiresColocatedDatabaseAndKeepsPostgresFactoriesSeparate() {
		Table table = table();
		table.getSpecifics().put("YSQL_COLOCATION", "true");
		assertTrue(sql(new Yugabyte15(), table).contains("yb_is_database_colocated()"));
		assertFalse(sql(com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("PostgreSQL", 15, 0, null),
				table).contains("COLOCATION"));
	}

	@Test
	void recreatesUniqueIndexPlacementBeforeAttachingConstraintWithoutMutatingModel() throws Exception {
		for (Dialect dialect : java.util.List.of(new Yugabyte11(), new Yugabyte15())) {
			var table = table();
			var unique = table.getConstraints().addUniqueConstraint("uq", table.getColumns().get("id"));
			unique.getSpecifics().put("YSQL_HASH_COLUMNS", "0");
			unique.getSpecifics().put("YSQL_TABLESPACE", "index space");
			unique.getSpecifics().put("YSQL_SPLIT_AT_BASE64", java.util.Base64.getEncoder()
					.encodeToString("SPLIT AT VALUES ((10))".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
			String before = restored.asXml();
			String sql = sql(dialect, restored);
			assertTrue(sql.contains("CREATE UNIQUE INDEX"), sql);
			assertTrue(sql.contains("UNIQUE USING INDEX"), sql);
			assertTrue(sql.contains("SPLIT AT VALUES ((10))"), sql);
			assertTrue(sql.indexOf("CREATE UNIQUE INDEX") < sql.indexOf("UNIQUE USING INDEX"), sql);
			assertEquals(before, restored.asXml());
		}
	}
}
