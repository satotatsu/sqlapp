/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings.*;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.metadata.AbstractCatalogObjectMetadataReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * One bulk local query for every user-defined type mapping and all native
 * conditions.
 */
public class Db2TypeMappingReader {
	private final QueryAccess access;
	private final Dialect dialect;

	public Db2TypeMappingReader(Dialect dialect) {
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
		Db2TypeMappings.set(catalog, read(connection));
	}

	protected Snapshot read(Connection connection, ParametersContext context) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 type mapping reader requires LUW 11.1 or later");
		List<Mapping> rows = new ArrayList<>();
		var times = new HashSet<String>();
		boolean[] orderKnown = { true };
		execute(connection, access.node(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String direction = rs.getString("MAPPINGDIRECTION");
				if (!"F".equals(direction) && !"R".equals(direction))
					throw new SQLException("Unknown Db2 type mapping direction: " + direction);
				var attributes = new LinkedHashMap<String, String>();
				for (String key : Db2TypeMappings.ATTRIBUTES) {
					String value = rs.getString(key);
					if (value != null)
						attributes.put(key, value);
				}
				rows.add(new Mapping(rs.getString("TYPE_MAPPING"),
						"F".equals(direction) ? Direction.FORWARD : Direction.REVERSE, attributes,
						rs.getString("REMARKS"), null));
				String time = rs.getString("CREATE_TIME");
				if (time == null || !times.add(time))
					orderKnown[0] = false;
			}
		});
		return new Snapshot(1, rows, orderKnown[0]);
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
			return getSqlNodeCache(Db2TypeMappingReader.class).getString("typeMappings.sql");
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
			throw new UnsupportedOperationException("Type mappings use a typed snapshot");
		}
	}
}
