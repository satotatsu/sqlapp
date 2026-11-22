package com.sqlapp.data.db.dialect.phoenix;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.View;

/**
 * Opt-in native DDL extraction for explicitly selected objects on a verified
 * Phoenix 5.3.2 deployment.
 */
public final class PhoenixNativeDefinitions {
	private static final Pattern VIEW = Pattern.compile("(?is)^\\s*CREATE\\s+VIEW\\b");
	private static final Pattern INDEX = Pattern.compile("(?is)^\\s*CREATE\\s+(?:LOCAL\\s+)?INDEX\\b");

	private PhoenixNativeDefinitions() {
	}

	public static void read(Connection connection, View view) throws SQLException {
		view.setDefinition(read(connection, view.getCatalogName(), view.getSchemaName(), view.getName(), VIEW));
	}

	public static void read(Connection connection, Index index) throws SQLException {
		index.getSpecifics().put(PhoenixCreateIndexFactory.NATIVE_DEFINITION,
				read(connection, index.getCatalogName(), index.getSchemaName(), index.getName(), INDEX));
	}

	private static String read(Connection connection, String tenant, String schema, String name, Pattern kind)
			throws SQLException {
		if (tenant != null && !tenant.isEmpty())
			throw new UnsupportedOperationException("Phoenix native DDL extraction supports global objects only");
		final String catalog = connection.getCatalog();
		if (catalog != null && !catalog.isEmpty())
			throw new UnsupportedOperationException("Phoenix native DDL extraction requires a global connection");
		final var metadata = connection.getMetaData();
		// The embedded driver exposes only the client major/minor in JDBC metadata.
		if (!"Phoenix".equalsIgnoreCase(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() != 5
				|| metadata.getDatabaseMinorVersion() != 3)
			throw new UnsupportedOperationException("Phoenix native DDL extraction requires a Phoenix 5.3 client");
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Phoenix native DDL extraction requires an object name");
		final String qualified = schema == null || schema.isEmpty() ? quote(name) : quote(schema) + "." + quote(name);
		// SHOW CREATE TABLE accepts one object name. Keep this separate from the bulk
		// catalog reader.
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SHOW CREATE TABLE " + qualified)) {
			if (!rows.next())
				throw new SQLException("Phoenix returned no native DDL for " + qualified);
			final String ddl = rows.getString(1);
			if (ddl == null || !kind.matcher(ddl).find() || rows.next())
				throw new SQLException("Phoenix returned unexpected native DDL for " + qualified);
			return ddl;
		}
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
