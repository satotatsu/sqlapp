/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** HSQLDB 2.x distinct scalar types; source type SQL remains opaque. */
public class HsqlTypeReader extends TypeReader {
	protected HsqlTypeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Type> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Type> result = new ArrayList<>();
		if (version.getMajorVersion() < 2)
			return result;
		execute(connection, getSqlNodeCache().getString("types.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var type = new Type(getString(rs, "USER_DEFINED_TYPE_NAME"));
				type.setCatalogName(getString(rs, "USER_DEFINED_TYPE_CATALOG"));
				type.setSchemaName(getString(rs, "USER_DEFINED_TYPE_SCHEMA"));
				String source = getString(rs, "SOURCE_DTD_IDENTIFIER");
				if (source == null || source.isBlank()) {
					throw new IllegalArgumentException(
							"HSQLDB distinct type has no source definition: " + type.getName());
				}
				if (getReaderOptions().isReadStatement())
					type.setStatement(source);
				if (getReaderOptions().isReadDefinition()) {
					var sql = getDialect().createSqlBuilder().create().type().name(type, true).as().space()
							._add(source);
					type.setDefinition(sql.toString());
				}
				result.add(type);
			}
		});
		return result;
	}

	@Override
	protected TypeColumnReader newColumnFactory() {
		return null;
	}
}
