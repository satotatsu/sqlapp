/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings.*;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Two bulk local-catalog queries, independent of the number of mappings. */
public class Db2FunctionMappingReader {
	private final QueryAccess access;
	private final Dialect dialect;

	public Db2FunctionMappingReader(Dialect dialect) {
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
		Db2FunctionMappings.set(catalog, read(connection));
	}

	protected Snapshot read(Connection connection, ParametersContext context) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 function mapping reader requires LUW 11.1 or later");
		var rows = new LinkedHashMap<String, Mapping>();
		var options = new LinkedHashMap<String, Map<String, String>>();
		var parameters = new LinkedHashMap<String, List<ParameterOption>>();
		var times = new HashSet<String>();
		boolean[] orderKnown = { true };
		execute(connection, access.node("functionMappings.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = rs.getString("FUNCTION_MAPPING");
				Mapping row = new Mapping(name, rs.getString("FUNCSCHEMA"), rs.getString("FUNCNAME"),
						rs.getString("SPECIFICNAME"), rs.getString("SERVERNAME"), rs.getString("SERVERTYPE"),
						rs.getString("SERVERVERSION"), rs.getString("WRAPNAME"), null, Map.of(), List.of(),
						rs.getString("REMARKS"), null);
				Mapping previous = rows.putIfAbsent(name, row);
				if (previous == null) {
					String time = rs.getString("CREATE_TIME");
					if (time == null || !times.add(time))
						orderKnown[0] = false;
				}
				if (previous != null && !previous.equals(row))
					throw new SQLException("Inconsistent Db2 function mapping identity: " + name);
				var values = options.computeIfAbsent(name, key -> new LinkedHashMap<>());
				parameters.computeIfAbsent(name, key -> new ArrayList<>());
				String option = rs.getString("OPTION_NAME");
				if (option != null) {
					String value = rs.getString("SETTING");
					if (value == null || values.putIfAbsent(option.toUpperCase(Locale.ROOT), value) != null)
						throw new SQLException("Missing or duplicate Db2 function mapping option: " + name);
				}
			}
		});
		execute(connection, access.node("functionMappingParameters.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String name = rs.getString("FUNCTION_MAPPING");
				var values = parameters.get(name);
				if (values == null)
					throw new SQLException("Orphan Db2 function mapping parameter: " + name);
				int ordinal = rs.getInt("ORDINAL");
				if (rs.wasNull())
					throw new SQLException("Missing Db2 function mapping parameter ordinal");
				values.add(new ParameterOption(ordinal, rs.getString("LOCATION"), rs.getString("OPTION_NAME"),
						rs.getString("SETTING")));
			}
		});
		List<Mapping> result = new ArrayList<>();
		rows.forEach((name,
				row) -> result.add(new Mapping(name, row.functionSchema(), row.functionName(), row.specificName(),
						row.serverName(), row.serverType(), row.serverVersion(), row.wrapperName(), null,
						options.get(name), parameters.get(name), row.remarks(), null)));
		return new Snapshot(1, result, orderKnown[0]);
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

		SqlNode node(String name) {
			return getSqlNodeCache(Db2FunctionMappingReader.class).getString(name);
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
			throw new UnsupportedOperationException("Function mappings use a typed snapshot");
		}
	}
}
