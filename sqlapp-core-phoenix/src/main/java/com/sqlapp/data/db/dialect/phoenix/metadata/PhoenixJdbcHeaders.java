/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixDdlOptions;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

/** Optional Phoenix fields are part of getTables; no extra catalog lookup. */
final class PhoenixJdbcHeaders {
	private PhoenixJdbcHeaders() {
	}

	static List<Table> read(Connection connection, String catalog, String schema, String name, boolean views,
			ReaderOptions options) {
		return read(connection, catalog, schema, name, views, options, null);
	}

	static List<Table> read(Connection connection, String catalog, String schema, String name, boolean views,
			ReaderOptions options, ProductVersionInfo version) {
		final List<Table> result = new ArrayList<>();
		final Set<List<String>> identities = new HashSet<>();
		try (var rs = new ExResultSet(connection.getMetaData().getTables(CommonUtils.emptyToNull(catalog),
				CommonUtils.emptyToNull(schema), CommonUtils.emptyToNull(name),
				views ? new String[] { "VIEW" } : new String[] { "TABLE", "SYSTEM TABLE" }))) {
			while (rs.next()) {
				final Table table = views ? new View(rs.getString("TABLE_NAME"))
						: new Table(rs.getString("TABLE_NAME"));
				table.setCatalogName(rs.getString("TABLE_CAT"));
				table.setSchemaName(rs.getString("TABLE_SCHEM"));
				if (table.getName() == null || table.getName().isBlank())
					throw new IllegalStateException("Phoenix table/view metadata requires a name");
				if (!identities.add(Arrays.asList(table.getCatalogName(), table.getSchemaName(), table.getName())))
					throw new IllegalStateException("Duplicate Phoenix table/view metadata: " + table.getCatalogName()
							+ "." + table.getSchemaName() + "." + table.getName());
				table.setRemarks(rs.getString("REMARKS"));
				final Long salt = rs.getLongValue("SALT_BUCKETS");
				if (salt != null && (salt < 0 || salt > 256))
					throw new IllegalStateException(
							"Invalid Phoenix SALT_BUCKETS metadata for " + table.getName() + ": " + salt);
				if (salt != null && salt > 0)
					table.getSpecifics().put(PhoenixDdlOptions.SALT_BUCKETS, salt);
				final Boolean immutable = booleanValue(rs, "IMMUTABLE_ROWS");
				if (immutable != null)
					table.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, immutable);
				final Boolean tenant = booleanValue(rs, "MULTI_TENANT");
				if (tenant != null)
					table.getSpecifics().put(PhoenixDdlOptions.MULTI_TENANT, tenant);
				final Boolean transactional = booleanValue(rs, "TRANSACTIONAL");
				if (transactional != null) {
					table.getSpecifics().put(PhoenixDdlOptions.TRANSACTIONAL, transactional);
				}
				final String provider = rs.getString("TRANSACTION_PROVIDER");
				if (provider != null) {
					if (provider.isBlank())
						throw new IllegalStateException("Blank Phoenix TRANSACTION_PROVIDER metadata");
					table.getSpecifics().put(PhoenixDdlOptions.TRANSACTION_PROVIDER, provider);
				}
				if (views) {
					table.getSpecifics().put("phoenix.viewMetadataOnly", true);
					final String type = rs.getString("VIEW_TYPE");
					if (type != null)
						table.getSpecifics().put("phoenix.viewType", type);
					if (options.isReadDefinition() || options.isReadStatement()) {
						final String statement = rs.getString("VIEW_STATEMENT");
						if (statement != null && !statement.isBlank())
							table.setStatement(statement);
					}
				}
				result.add(table);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not read Phoenix table/view headers", e);
		}
		if (!views) {
			try {
				PhoenixDefaultFamilyReader.read(connection, result, version);
			} catch (SQLException e) {
				throw new IllegalStateException("Could not read Phoenix table properties from SYSTEM.CATALOG", e);
			}
		}
		return result;
	}

	private static Boolean booleanValue(ExResultSet rs, String field) throws SQLException {
		final String value = rs.getString(field);
		if (value == null)
			return null;
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
			throw new IllegalStateException("Invalid Phoenix " + field + " metadata: " + value);
		return Boolean.valueOf(value);
	}
}
