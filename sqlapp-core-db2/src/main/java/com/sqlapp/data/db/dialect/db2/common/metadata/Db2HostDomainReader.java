/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** DISTINCT and array types use the same Domain representation as Db2 LUW. */
final class Db2HostDomainReader extends DomainReader {
	private final Class<?> resources;

	Db2HostDomainReader(Dialect dialect, Class<?> resources) {
		super(dialect);
		this.resources = resources;
	}

	@Override
	protected List<Domain> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Domain> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(resources).getString("domains.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var type = new Domain(required(rs, "DOMAIN_NAME"));
				type.setSchemaName(required(rs, "SCHEMA_NAME"));
				String kind = required(rs, "METATYPE"), source = required(rs, "SOURCE_TYPE"),
						schema = required(rs, "SOURCE_SCHEMA");
				if (!Set.of("T", "A", "L").contains(kind))
					throw new SQLException("Unknown host type kind: " + kind);
				var fields = type.getSpecifics();
				fields.put("DB2_OBJECT_TYPE", "DATA_TYPE");
				for (String field : List.of("METATYPE", "SOURCE_TYPE", "SOURCE_SCHEMA", "TYPE_RULES", "NATIVE_LENGTH",
						"CCSID", "NORMALIZE_DATA", "SUBTYPE", "ENCODING_SCHEME", "ARRAY_CARDINALITY",
						"ARRAY_INDEX_TYPE", "ARRAY_INDEX_LENGTH", "ARRAY_INDEX_SUBTYPE", "INLINE_LENGTH")) {
					String value = id(rs, field);
					if (value != null)
						fields.put("DB2_HOST_" + field, value);
				}
				Long length = rs.getLongValue("TYPE_LENGTH");
				Integer scale = rs.getInteger("TYPE_SCALE");
				if (!"SYSIBM".equals(schema) || !getDialect().setDbType(source, length, scale, type)) {
					type.setDataType(DataType.OTHER);
					type.setDataTypeName(Db2CreationContext.qualified(schema, source));
					type.setLength(length);
					type.setScale(scale);
				}
				// Generic BLOB mappings omit size; a DISTINCT source requires its catalog
				// length.
				if ("SYSIBM".equals(schema) && "BLOB".equals(source))
					type.setLength(length);
				if (!"T".equals(kind)) {
					type.setArrayDimension(1);
					Long cardinality = rs.getLongValue("ARRAY_CARDINALITY");
					if ("A".equals(kind) && cardinality != null && cardinality > 0 && cardinality <= Integer.MAX_VALUE)
						type.setArrayDimensionUpperBound(cardinality.intValue());
				}
				type.setCreatedAt(rs.getTimestamp("CREATED_AT"));
				type.setRemarks(rs.getString("REMARKS"));
				result.add(type);
			}
		});
		return result;
	}

	private static String required(ExResultSet rs, String name) throws SQLException {
		String value = id(rs, name);
		if (value == null)
			throw new SQLException("Missing host type field: " + name);
		return value;
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null || value.isBlank() ? null : value.stripTrailing();
	}
}
