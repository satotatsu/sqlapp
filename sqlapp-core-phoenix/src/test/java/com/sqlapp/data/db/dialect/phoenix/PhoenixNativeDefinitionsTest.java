package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.schemas.SchemaUtils;

class PhoenixNativeDefinitionsTest {
	@Test
	void storesNativeDdlThroughXmlAndRejectsTenantObjectsBeforeQuerying() throws Exception {
		final String ddl = "CREATE VIEW \"Sales\".\"View One\" AS SELECT * FROM BASE";
		final var response = new java.util.concurrent.atomic.AtomicReference<>(ddl);
		final var calls = new AtomicInteger();
		final var rows = proxy(ResultSet.class, (method, args) -> switch (method) {
		case "next" -> calls.getAndIncrement() == 0;
		case "getString" -> response.get();
		default -> null;
		});
		final var statement = proxy(Statement.class, (method, args) -> {
			if (!method.equals("executeQuery"))
				return null;
			assertEquals("SHOW CREATE TABLE \"Sales\".\"View One\"", args[0]);
			return rows;
		});
		final var metadata = proxy(DatabaseMetaData.class, (method, args) -> switch (method) {
		case "getDatabaseProductName" -> "Phoenix";
		case "getDatabaseMajorVersion" -> 5;
		case "getDatabaseMinorVersion" -> 3;
		default -> throw new AssertionError(method);
		});
		final var connection = proxy(Connection.class, (method, args) -> switch (method) {
		case "getCatalog" -> "";
		case "getMetaData" -> metadata;
		case "createStatement" -> statement;
		default -> throw new AssertionError(method);
		});
		final var view = new View("View One");
		view.setSchemaName("Sales");
		view.setCatalogName("tenant");
		assertThrows(UnsupportedOperationException.class, () -> PhoenixNativeDefinitions.read(connection, view));
		assertEquals(0, calls.get());
		view.setCatalogName(null);
		PhoenixNativeDefinitions.read(connection, view);
		final View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		assertEquals(ddl, String.join("\n", restored.getDefinition()));
		calls.set(0);
		response.set("CREATE VIEW \"Sales\".\"View One\" AS SELECT * FROM \"Sales\".\"View One\"");
		assertThrows(SQLException.class, () -> PhoenixNativeDefinitions.read(connection, view));
		assertEquals(ddl, String.join("\n", view.getDefinition()));
		view.getSpecifics().put("phoenix.viewType", "MAPPED");
		calls.set(0);
		assertThrows(UnsupportedOperationException.class, () -> PhoenixNativeDefinitions.read(connection, view));
		assertEquals(0, calls.get());
	}

	private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> call) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> call.apply(method.getName(), args)));
	}
}
