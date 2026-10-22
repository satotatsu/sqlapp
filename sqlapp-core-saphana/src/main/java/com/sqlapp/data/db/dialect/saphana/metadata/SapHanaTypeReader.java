/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeColumn;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** HANA stores user-defined table types in TABLES/TABLE_COLUMNS. */
public class SapHanaTypeReader extends TypeReader {
	protected SapHanaTypeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Type> doGetAll(final Connection connection, final ParametersContext context, final ProductVersionInfo version) {
		final Map<List<String>, Type> types = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("types.sql"), context, new ResultSetNextHandler() {
			@Override public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String schema = getString(rs, SCHEMA_NAME);
				final String name = getString(rs, TYPE_NAME);
				final Type type = types.computeIfAbsent(List.of(schema, name), ignored -> new Type(name).setSchemaName(schema));
				type.setDialect(getDialect());
				if (rs.getString("TABLE_COLUMN_NAME") != null) {
					final var column = SapHanaRoutineArguments.readColumn(getDialect(), rs);
					type.getColumns().add(new TypeColumn(column.getName()).setDataType(column.getDataType())
							.setDataTypeName(column.getDataTypeName()).setLength(column.getLength()).setScale(column.getScale())
							.setNullable(!column.isNotNull()));
				}
			}
		});
		return new ArrayList<>(types.values());
	}

	@Override protected TypeColumnReader newColumnFactory() { return null; }
}
