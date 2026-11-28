/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixDdlOptions;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * Supplements JDBC headers with one catalog query for the requested table set.
 */
final class PhoenixDefaultFamilyReader {
	private record Identity(String tenant, String schema, String table) {
		static Identity of(String tenant, String schema, String table) {
			return new Identity(CommonUtils.emptyToNull(tenant), CommonUtils.emptyToNull(schema), table);
		}
	}

	private PhoenixDefaultFamilyReader() {
	}

	private record Properties(String family, Boolean disableWal, Boolean appendOnly, String updateCacheFrequency,
			String guidePostsWidth, Boolean storeNulls) {
	}

	static void read(Connection connection, List<Table> tables, ProductVersionInfo version) throws SQLException {
		// Do not require additional physical catalog columns on older/unknown servers.
		if (tables.isEmpty() || version == null || version.getMajorVersion() == null
				|| !(version.getMajorVersion() > 4 || (version.getMajorVersion() == 4
						&& version.getMinorVersion() != null && version.getMinorVersion() >= 1)))
			return;
		final Map<Identity, Table> requested = new LinkedHashMap<>();
		for (var table : tables)
			requested.put(Identity.of(table.getCatalogName(), table.getSchemaName(), table.getName()), table);
		final List<String> names = tables.stream().map(Table::getName).distinct().toList();
		final List<String> tenants = requested.keySet().stream().map(Identity::tenant).distinct().toList();
		final List<String> schemas = requested.keySet().stream().map(Identity::schema).distinct().toList();
		final List<String> parameters = new ArrayList<>();
		final boolean hasAppendOnly = version.getMajorVersion() > 4 || (version.getMajorVersion() == 4
				&& version.getMinorVersion() != null && version.getMinorVersion() >= 8);
		final boolean hasCacheFrequency = version.getMajorVersion() > 4 || version.getMinorVersion() >= 7;
		final boolean hasGuidePosts = version.getMajorVersion() > 4 || version.getMinorVersion() >= 9;
		final boolean hasStoreNulls = version.getMajorVersion() > 4 || version.getMinorVersion() >= 3;
		final StringBuilder sql = new StringBuilder(
				"SELECT TENANT_ID, TABLE_SCHEM, TABLE_NAME, DEFAULT_COLUMN_FAMILY, DISABLE_WAL");
		if (hasAppendOnly)
			sql.append(", APPEND_ONLY_SCHEMA");
		if (hasCacheFrequency)
			sql.append(", UPDATE_CACHE_FREQUENCY");
		if (hasGuidePosts)
			sql.append(", GUIDE_POSTS_WIDTH");
		if (hasStoreNulls)
			sql.append(", STORE_NULLS");
		sql.append(" FROM SYSTEM.CATALOG WHERE COLUMN_NAME IS NULL AND COLUMN_FAMILY IS NULL AND ");
		filter(sql, "TABLE_NAME", names, parameters);
		sql.append(" AND ");
		filter(sql, "TENANT_ID", tenants, parameters);
		sql.append(" AND ");
		filter(sql, "TABLE_SCHEM", schemas, parameters);
		final Map<Identity, Properties> families = new LinkedHashMap<>();
		try (var statement = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < parameters.size(); i++)
				statement.setString(i + 1, parameters.get(i));
			try (var rs = statement.executeQuery()) {
				while (rs.next()) {
					final var id = Identity.of(rs.getString("TENANT_ID"), rs.getString("TABLE_SCHEM"),
							rs.getString("TABLE_NAME"));
					if (!requested.containsKey(id))
						continue; // A name/schema/tenant cross-product can include other objects.
					if (families.containsKey(id))
						throw new IllegalStateException("Duplicate Phoenix default-family header: " + id);
					final String family = rs.getString("DEFAULT_COLUMN_FAMILY");
					if (family != null && family.isBlank())
						throw new IllegalStateException("Blank Phoenix default column family: " + id);
					families.put(id, new Properties(family, booleanValue(rs.getString("DISABLE_WAL"), "DISABLE_WAL"),
							hasAppendOnly ? booleanValue(rs.getString("APPEND_ONLY_SCHEMA"), "APPEND_ONLY_SCHEMA")
									: null,
							hasCacheFrequency
									? PhoenixDdlOptions.normalizeTuningValue(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY,
											rs.getString("UPDATE_CACHE_FREQUENCY"))
									: null,
							hasGuidePosts
									? PhoenixDdlOptions.normalizeTuningValue(PhoenixDdlOptions.GUIDE_POSTS_WIDTH,
											Objects.requireNonNullElse(rs.getString("GUIDE_POSTS_WIDTH"), "NULL"))
									: null,
							hasStoreNulls ? booleanValue(rs.getString("STORE_NULLS"), "STORE_NULLS") : null));
				}
			}
		}
		if (!families.keySet().containsAll(requested.keySet()))
			throw new IllegalStateException("Phoenix default-family metadata is incomplete; "
					+ "a requested table disappeared or is not visible in SYSTEM.CATALOG");
		// Publish only after the entire result has been validated.
		families.forEach((id, properties) -> {
			final var specifics = requested.get(id).getSpecifics();
			if (properties.family() != null)
				specifics.put(PhoenixDdlOptions.DEFAULT_COLUMN_FAMILY, properties.family());
			if (properties.disableWal() != null)
				specifics.put(PhoenixDdlOptions.DISABLE_WAL, properties.disableWal());
			if (properties.appendOnly() != null)
				specifics.put(PhoenixDdlOptions.APPEND_ONLY_SCHEMA, properties.appendOnly());
			if (properties.updateCacheFrequency() != null)
				specifics.put(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, properties.updateCacheFrequency());
			if (properties.guidePostsWidth() != null)
				specifics.put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, properties.guidePostsWidth());
			if (properties.storeNulls() != null)
				specifics.put(PhoenixDdlOptions.STORE_NULLS, properties.storeNulls());
		});
	}

	private static Boolean booleanValue(String value, String column) {
		if (value == null)
			return null;
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
			throw new IllegalStateException("Invalid Phoenix " + column + " metadata: " + value);
		return Boolean.valueOf(value);
	}

	private static void filter(StringBuilder sql, String column, List<String> values, List<String> parameters) {
		final List<String> nonNull = values.stream().filter(Objects::nonNull).toList();
		sql.append('(');
		if (values.contains(null)) {
			sql.append(column).append(" IS NULL");
			if (!nonNull.isEmpty())
				sql.append(" OR ");
		}
		if (!nonNull.isEmpty()) {
			sql.append(column).append(" IN (")
					.append(String.join(",", java.util.Collections.nCopies(nonNull.size(), "?"))).append(')');
			parameters.addAll(nonNull);
		}
		sql.append(')');
	}
}
