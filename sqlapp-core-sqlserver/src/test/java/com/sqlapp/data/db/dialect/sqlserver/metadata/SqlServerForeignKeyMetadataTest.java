/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.ResultSet;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;

class SqlServerForeignKeyMetadataTest {
	@Test
	void preservesEnabledTrustAndReplicationStatesInXml() throws Exception {
		var reader = new SqlServer2005ForeignKeyConstraintReader(DialectHolder.defaultDialect2005);
		for (boolean flag : new boolean[] { false, true }) {
			Map<String, Object> values = Map.of("constraint_name", "FK_child_parent", "schema_name", "dbo",
					"table_name", "child", "catalog_name", "catalog", "is_disabled", flag ? 1 : 0, "is_not_trusted",
					flag, "is_not_for_replication", flag, "update_referential_action_desc", "NO_ACTION",
					"delete_referential_action_desc", "CASCADE");
			ResultSet row = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
						Object value = args != null && args.length > 0 ? values.get(args[0]) : null;
						return switch (method.getName()) {
						case "getString", "getNString" -> value == null ? null : value.toString();
						case "getObject" -> value;
						case "getInt" -> value == null ? 0 : ((Number) value).intValue();
						default -> null;
						};
					});
			ForeignKeyConstraint constraint = reader.createForeignKeyConstraint(new ExResultSet(row) {
				@Override
				public boolean contains(String label) {
					return values.containsKey(label);
				}
			});
			assertEquals(!flag, constraint.isEnable());
			assertEquals(Boolean.toString(flag), constraint.getSpecifics().get("is_not_trusted"));
			assertEquals(Boolean.toString(flag), constraint.getSpecifics().get("is_not_for_replication"));
			var table = new com.sqlapp.data.schemas.Table("child");
			table.getConstraints().add(constraint);
			com.sqlapp.data.schemas.Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(constraint.getSpecifics(), restored.getConstraints().get(0).getSpecifics());
			assertEquals(constraint.isEnable(), restored.getConstraints().get(0).isEnable());
		}
	}

	@Test
	void ordersCompositeKeysByConstraintOrdinal() throws Exception {
		try (var stream = getClass().getResourceAsStream("foreignKeyConstraints2005.sql")) {
			assertNotNull(stream);
			String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(sql.contains("fk.name, fkc.constraint_column_id"));
			assertFalse(sql.contains("fk.name, fkc.parent_column_id"));
		}
	}
}
