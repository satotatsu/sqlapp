/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.*;
import com.sqlapp.data.db.dialect.db2.Db2UserMappings.Principal;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** One local catalog query; encrypted passwords are never fetched as values. */
public class Db2UserMappingReader {
	private final QueryAccess access;
	private final Dialect dialect;

	public Db2UserMappingReader(Dialect dialect) {
		this.dialect = dialect;
		access = new QueryAccess(dialect);
	}

	AbstractCatalogObjectMetadataReader<?> metadataReader() {
		return access;
	}

	public Snapshot read(Connection connection) {
		return read(connection, access.context(connection));
	}

	public void loadFull(Connection connection, Catalog catalog) {
		Db2UserMappings.set(catalog, read(connection).mappings());
	}

	protected Snapshot read(Connection connection, ParametersContext context) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 user mapping reader requires LUW 11.1 or later");
		var identities = new LinkedHashMap<List<String>, Principal>();
		var options = new LinkedHashMap<List<String>, Map<String, String>>();
		var missing = new LinkedHashMap<List<String>, List<String>>();
		execute(connection, access.node(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String user = rs.getString("AUTHID"), server = rs.getString("SERVERNAME");
				if (!"U".equals(rs.getString("AUTHIDTYPE")))
					throw new SQLException("Unsupported Db2 user mapping authorization type");
				if (user == null || server == null)
					throw new SQLException("Missing Db2 user mapping identity");
				var id = List.of(user, server);
				identities.putIfAbsent(id, "PUBLIC".equals(user) ? Principal.PUBLIC : Principal.USER);
				var values = options.computeIfAbsent(id, key -> new LinkedHashMap<>());
				var unavailable = missing.computeIfAbsent(id, key -> new ArrayList<>());
				String option = rs.getString("OPTION_NAME");
				if (option == null)
					throw new SQLException("Missing Db2 user mapping option name");
				option = option.toUpperCase(java.util.Locale.ROOT);
				if (values.containsKey(option) || unavailable.contains(option))
					throw new SQLException("Duplicate Db2 user mapping option: " + option);
				if ("REMOTE_PASSWORD".equals(option))
					unavailable.add(option);
				else {
					String value = rs.getString("SETTING");
					if (value == null)
						throw new SQLException("Missing Db2 user mapping option value: " + option);
					values.put(option, value);
				}
			}
		});
		List<Mapping> mappings = new ArrayList<>();
		identities.forEach((id, principal) -> mappings
				.add(new Mapping(principal, id.get(0), id.get(1), options.get(id), missing.get(id), null)));
		return new Snapshot(1, mappings);
	}

	protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
			ResultSetNextHandler handler) {
		return access.query(connection, node, context, handler);
	}

	private static final class QueryAccess extends AbstractCatalogObjectMetadataReader<Setting> {
		QueryAccess(Dialect dialect) {
			super(dialect);
		}

		ParametersContext context(Connection connection) {
			return defaultParametersContext(connection);
		}

		SqlNode node() {
			return getSqlNodeCache(Db2UserMappingReader.class).getString("userMappings.sql");
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
			throw new UnsupportedOperationException("User mappings use a typed snapshot");
		}
	}
}
