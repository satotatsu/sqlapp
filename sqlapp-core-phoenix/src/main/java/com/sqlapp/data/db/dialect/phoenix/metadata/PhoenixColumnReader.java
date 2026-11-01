/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixCreateTableFactory;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;

/** Preserves Phoenix's extra JDBC column fields without additional queries. */
public class PhoenixColumnReader extends JdbcColumnReader {
	public PhoenixColumnReader(Dialect dialect) {
		super(dialect);
	}

	private record Identity(String catalog, String schema, String table, String column) {
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		final var columns = super.doGetAll(connection, context, version);
		final Map<Identity, Column> names = new HashMap<>();
		for (var column : columns) {
			final var identity = new Identity(column.getCatalogName(), column.getSchemaName(), column.getTableName(),
					column.getName());
			final var previous = names.putIfAbsent(identity, column);
			if (previous != null
					&& !java.util.Objects.equals(previous.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY),
							column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY)))
				throw new UnsupportedOperationException(
						"Phoenix columns with the same name in different families cannot be represented "
								+ "by the current Schema column identity: " + identity);
		}
		return columns;
	}

	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException {
		final var column = super.createColumn(rs);
		final String family = rs.getString("COLUMN_FAMILY");
		if (family != null && !family.isEmpty())
			column.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, family);
		final Long capacity = rs.getLongValue("ARRAY_SIZE");
		if (capacity != null) {
			if (capacity < 0)
				throw new IllegalStateException(
						"Invalid Phoenix ARRAY_SIZE for column " + column.getName() + ": " + capacity);
			if (capacity > 0) {
				column.setArrayDimension(1);
				column.setArrayDimensionUpperBound(Math.toIntExact(capacity));
			}
		}
		if (rs.getInt("DATA_TYPE") == java.sql.Types.ARRAY)
			column.setArrayDimension(1);
		column.setRemarks(rs.getString("REMARKS"));
		column.setDefaultValue(rs.getString("COLUMN_DEF"));
		column.setOctetLength(rs.getLongValue("CHAR_OCTET_LENGTH"));
		return column;
	}
}
