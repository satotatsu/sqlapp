/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2Federation;
import com.sqlapp.data.db.dialect.db2.Db2Federation.*;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Three set queries, including objects with no options and category defaults.
 */
public class Db2FederationReader {
	private static final Set<String> DEFAULT_WRAPPERS = Set.of("DRDA", "NET8", "ODBC", "INFORMIX", "JDBC", "NOSQL",
			"NoSQL");
	private final QueryAccess access;

	public Db2FederationReader(Dialect dialect) {
		access = new QueryAccess(dialect);
	}

	AbstractCatalogObjectMetadataReader<?> metadataReader() {
		return access;
	}

	public void loadFull(Connection connection, Catalog catalog) {
		Snapshot snapshot = read(connection);
		Db2Federation.set(catalog, snapshot.wrappers(), snapshot.servers(), snapshot.defaults());
	}

	/**
	 * Ordinary callers use CatalogReader; direct access returns the typed snapshot.
	 */
	public Snapshot read(Connection connection) {
		return read(connection, access.context(connection));
	}

	protected Snapshot read(Connection connection, ParametersContext context) {
		var wrappers = new LinkedHashMap<String, Wrapper>();
		var wrapperOptions = new LinkedHashMap<String, Map<String, String>>();
		query(connection, "federationWrappers.sql", context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = rs.getString("WRAPNAME");
				wrappers.putIfAbsent(name, new Wrapper(name, rs.getString("LIBRARY"), DEFAULT_WRAPPERS.contains(name),
						Map.of(), rs.getString("REMARKS"), null,
						Map.of("WRAPTYPE", rs.getString("WRAPTYPE"), "WRAPVERSION", rs.getString("WRAPVERSION"))));
				option(wrapperOptions.computeIfAbsent(name, k -> new LinkedHashMap<>()), rs);
			}
		});
		var servers = new LinkedHashMap<String, Server>();
		var serverOptions = new LinkedHashMap<String, Map<String, String>>();
		query(connection, "federationServers.sql", context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = rs.getString("SERVERNAME");
				servers.putIfAbsent(name, new Server(name, rs.getString("WRAPNAME"), rs.getString("SERVERTYPE"),
						rs.getString("SERVERVERSION"), Map.of(), Map.of(), rs.getString("REMARKS"), null));
				option(serverOptions.computeIfAbsent(name, k -> new LinkedHashMap<>()), rs);
			}
		});
		var defaults = new LinkedHashMap<List<String>, Map<String, String>>();
		query(connection, "federationDefaults.sql", context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var id = java.util.Arrays.asList(rs.getString("WRAPNAME"), rs.getString("SERVERTYPE"),
						rs.getString("SERVERVERSION"));
				option(defaults.computeIfAbsent(id, k -> new LinkedHashMap<>()), rs);
			}
		});
		List<Wrapper> wrapperList = wrappers.values().stream().map(w -> new Wrapper(w.name(), w.library(),
				w.defaultLibrary(), wrapperOptions.get(w.name()), w.remarks(), null, w.attributes())).toList();
		List<Server> serverList = servers.values().stream().map(s -> {
			var explicit = new LinkedHashMap<String, String>();
			var derived = new LinkedHashMap<String, String>();
			serverOptions.get(s.name()).forEach((key, value) -> {
				(Db2Federation.DERIVED_OPTIONS.contains(key.toUpperCase(java.util.Locale.ROOT)) ? derived : explicit)
						.put(key, value);
			});
			return new Server(s.name(), s.wrapperName(), s.type(), s.version(), explicit, derived, s.remarks(), null);
		}).toList();
		List<Defaults> defaultList = new ArrayList<>();
		defaults.forEach((id, options) -> defaultList.add(new Defaults(id.get(0), id.get(1), id.get(2), options)));
		return new Snapshot(1, wrapperList, serverList, defaultList);
	}

	private void query(Connection connection, String resource, ParametersContext context,
			ResultSetNextHandler handler) {
		execute(connection, access.node(resource), context, handler);
	}

	protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
			ResultSetNextHandler handler) {
		return access.query(connection, node, context, handler);
	}

	private void option(Map<String, String> options, ExResultSet rs) throws SQLException {
		String name = rs.getString("OPTION_NAME");
		if (name == null)
			return; // LEFT JOIN retains wrappers/servers with no options.
		String value = rs.getString("SETTING");
		if (value == null || options.putIfAbsent(name, value) != null)
			throw new SQLException("Missing value or duplicate Db2 federation option: " + name);
	}

	/**
	 * Reuses configured query execution without exposing a fictitious Settings API.
	 */
	private static final class QueryAccess extends AbstractCatalogObjectMetadataReader<Setting> {
		QueryAccess(Dialect dialect) {
			super(dialect);
		}

		SqlNode node(String resource) {
			return getSqlNodeCache(Db2FederationReader.class).getString(resource);
		}

		ParametersContext context(Connection connection) {
			return defaultParametersContext(connection);
		}

		JdbcQueryHandler query(Connection connection, SqlNode node, ParametersContext context,
				ResultSetNextHandler handler) {
			return execute(connection, node, context, handler);
		}

		@Override
		protected SchemaObjectProperties getSchemaObjectProperties() {
			return SchemaObjectProperties.SETTINGS;
		}

		@Override
		protected List<Setting> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			throw new UnsupportedOperationException("Federation uses a typed snapshot");
		}
	}
}
