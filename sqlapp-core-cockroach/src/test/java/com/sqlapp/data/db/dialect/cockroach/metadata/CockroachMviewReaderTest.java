/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class CockroachMviewReaderTest {
	@Test
	void bindsFiltersAndFetchesDefinitionSetsWithOneQuery() throws Exception {
		for (int count : List.of(1, 20)) {
			var queries = new AtomicInteger();
			var position = new AtomicInteger(-1);
			var bindings = new ArrayList<String>();
			var names = new ArrayList<String>();
			for (int i = 0; i < count; i++)
				names.add("view_" + i);
			String schema = "reports'quoted";
			var rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { ResultSet.class }, (proxy, method, args) -> switch (method.getName()) {
					case "next" -> position.incrementAndGet() < count;
					case "getString" -> switch ((Integer) args[0]) {
					case 1 -> schema;
					case 2 -> names.get(position.get());
					case 3 -> "SELECT 7";
					case 4 -> Integer.toString(position.get() + 100);
					case 5 -> "Reporting Owners";
					default -> throw new AssertionError(args[0]);
					};
					case "close" -> null;
					default -> throw new AssertionError(method.getName());
					});
			var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> switch (method.getName()) {
					case "setString" -> {
						bindings.add((String) args[1]);
						yield null;
					}
					case "executeQuery" -> {
						queries.incrementAndGet();
						yield rows;
					}
					case "close" -> null;
					default -> throw new AssertionError(method.getName());
					});
			var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						assertEquals("prepareStatement", method.getName());
						String sql = (String) args[0];
						assertTrue(sql.contains("v.schemaname IN (?)"));
						assertTrue(sql.contains("v.matviewname IN ("));
						assertFalse(sql.contains(schema));
						return statement;
					});
			var context = new ParametersContext();
			context.put("schemaName", schema);
			context.put("tableName", names);
			var result = new CockroachMviewReader(new CockroachDB()).doGetAll(connection, context, null);
			assertEquals(count, result.size());
			assertEquals(1, queries.get());
			assertEquals(schema, bindings.getFirst());
			assertEquals(names, bindings.subList(1, bindings.size()));
			assertEquals("100", result.getFirst().getId());
			assertEquals(List.of("SELECT 7"), result.getFirst().getStatement());
			assertEquals("Reporting Owners", ((com.sqlapp.data.schemas.Mview) result.getFirst()).getOwnerName());
			assertFalse(result.getFirst().getSpecifics().containsKey("COCKROACH_OWNER"));
			var schemaModel = new Schema(schema);
			schemaModel.getMviews().addAll(result);
			Schema restored = SchemaUtils.readXml(new StringReader(schemaModel.asXml()));
			assertEquals("Reporting Owners", restored.getMviews().get(names.getFirst()).getOwnerName());
		}
	}
}
