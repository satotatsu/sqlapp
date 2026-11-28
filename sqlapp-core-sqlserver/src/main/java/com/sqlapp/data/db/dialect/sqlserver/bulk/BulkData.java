/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.microsoft.sqlserver.jdbc.ISQLServerBulkData;
import com.microsoft.sqlserver.jdbc.SQLServerBulkCopyOptions;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.SqlServerGeometryJdbcTypeHandler;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.CommonUtils;

/** Streams a sqlapp Table's rows to the Microsoft JDBC bulk-copy API. */
public class BulkData implements ISQLServerBulkData, AutoCloseable {
	private static final java.util.regex.Pattern JSON_NUMBER = java.util.regex.Pattern
			.compile("-?(?:0|[1-9][0-9]*)(?:[.][0-9]+)?(?:[eE][+-]?[0-9]+)?");
	private final Table table;
	private final List<Column> columns = new ArrayList<>();
	private final Set<Integer> columnOrdinals = new LinkedHashSet<>();
	private final Iterator<Row> rows;
	private Row currentRow;
	private long rowCount;
	private boolean closed;

	public BulkData(final Table table, final BulkOption options) {
		this.table = java.util.Objects.requireNonNull(table, "table");
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		for (final Column column : table.getColumns()) {
			if (column.isHidden() || !CommonUtils.isEmpty(column.getFormula())
					|| (column.isIdentity() && !effective.isKeepIdentity())) {
				continue;
			}
			if (column.getDataType() == DataType.VECTOR && column.getSpecifics().get("vector_base_type") != null)
				throw new IllegalArgumentException(
						"Native SQL Server Bulk Copy supports FLOAT32 VECTOR only: " + column.getName());
			columns.add(column);
			columnOrdinals.add(columns.size());
		}
		this.rows = table.getRows().iterator();
	}

	public static SQLServerBulkCopyOptions toSqlServerOptions(final BulkOption options) throws SQLServerException {
		final BulkOption effective = options == null ? BulkOption.defaults() : options;
		final SQLServerBulkCopyOptions result = new SQLServerBulkCopyOptions();
		if (effective.getBatchSize() != null) {
			result.setBatchSize(effective.getBatchSize());
		}
		if (effective.getBulkCopyTimeout() != null) {
			result.setBulkCopyTimeout(effective.getBulkCopyTimeout());
		}
		result.setAllowEncryptedValueModifications(effective.isAllowEncryptedValueModifications());
		result.setCheckConstraints(effective.isCheckConstraints());
		result.setFireTriggers(effective.isFireTriggers());
		result.setKeepIdentity(effective.isKeepIdentity());
		result.setKeepNulls(effective.isKeepNulls());
		result.setTableLock(effective.isTableLock());
		result.setUseInternalTransaction(effective.isUseTransaction());
		return result;
	}

	@Override
	public Set<Integer> getColumnOrdinals() {
		return java.util.Collections.unmodifiableSet(columnOrdinals);
	}

	private Column getColumn(final int ordinal) {
		if (ordinal < 1 || ordinal > columns.size()) {
			throw new IllegalArgumentException("Invalid bulk column ordinal: " + ordinal);
		}
		return columns.get(ordinal - 1);
	}

	@Override
	public String getColumnName(final int ordinal) {
		return getColumn(ordinal).getName();
	}

	@Override
	public int getColumnType(final int ordinal) {
		final Column column = getColumn(ordinal);
		if (isGeometry(column)) {
			// geometry / geography are sent as the same serialized bytes that the regular
			// INSERT binds
			return java.sql.Types.VARBINARY;
		}
		if (column.getDataType() == DataType.VECTOR)
			return microsoft.sql.Types.VECTOR;
		if (column.getDataType() == null || column.getDataType().getJdbcType() == null) {
			return java.sql.Types.OTHER;
		}
		return column.getDataType().getJdbcType().getVendorTypeNumber();
	}

	@Override
	public int getPrecision(final int ordinal) {
		Column column = getColumn(ordinal);
		if (column.getDataType() == DataType.VECTOR) {
			Integer dimension = column.getVectorDimension();
			if (dimension == null || dimension < 1)
				throw new IllegalArgumentException("Missing VECTOR dimension for " + column.getName());
			return dimension;
		}
		final Long length = column.getLength();
		return length == null ? 0 : Math.toIntExact(length);
	}

	@Override
	public int getScale(final int ordinal) {
		Column column = getColumn(ordinal);
		if (column.getDataType() == DataType.VECTOR)
			return 4;
		final Integer scale = column.getScale();
		return scale == null ? 0 : scale;
	}

	@Override
	public Object[] getRowData() throws SQLException {
		if (currentRow == null) {
			throw new SQLException("next() must return true before getRowData()");
		}
		final Object[] values = new Object[columns.size()];
		for (int i = 0; i < columns.size(); i++) {
			final Column column = columns.get(i);
			final Object value = currentRow.get(column.getOrdinal());
			if (value != null && isGeometry(column)) {
				values[i] = SqlServerGeometryJdbcTypeHandler.toSqlServerBytes(
						Converters.getDefault().convertObject(value, org.geolatte.geom.Geometry.class));
			} else if (value != null && column.getDataType() == DataType.VECTOR) {
				values[i] = vectorValue(value, i + 1);
			} else {
				values[i] = value;
			}
		}
		return values;
	}

	private microsoft.sql.Vector vectorValue(Object value, int ordinal) throws SQLException {
		Column column = getColumn(ordinal);
		int dimension = getPrecision(ordinal);
		int scale = getScale(ordinal);
		try {
			String text = value.toString().strip();
			if (!text.startsWith("[") || !text.endsWith("]"))
				throw new IllegalArgumentException("Expected a JSON array");
			String[] elements = text.substring(1, text.length() - 1).split(",", -1);
			if (elements.length != dimension)
				throw new IllegalArgumentException("Expected " + dimension + " vector elements");
			Float[] data = new Float[dimension];
			for (int i = 0; i < dimension; i++) {
				String element = elements[i].strip();
				if (!JSON_NUMBER.matcher(element).matches())
					throw new IllegalArgumentException("Expected a JSON number");
				data[i] = Float.valueOf(element);
				if (!Float.isFinite(data[i]))
					throw new IllegalArgumentException("Vector element is outside the finite range");
			}
			return new microsoft.sql.Vector(dimension, scale, data);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Invalid VECTOR value for column " + column.getName(), e);
		}
	}

	@Override
	public boolean next() throws SQLException {
		try {
			if (!rows.hasNext()) {
				currentRow = null;
				close();
				return false;
			}
			currentRow = rows.next();
			rowCount++;
			return true;
		} catch (RuntimeException e) {
			throw new SQLException("Failed to read bulk row for " + table.getName(), e);
		}
	}

	private static boolean isGeometry(final Column column) {
		return column.getDataType() == DataType.GEOMETRY || column.getDataType() == DataType.GEOGRAPHY;
	}

	public long getRowCount() {
		return rowCount;
	}

	@Override
	public void close() throws SQLException {
		if (closed) {
			return;
		}
		closed = true;
		currentRow = null;
		if (rows instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception e) {
				throw new SQLException("Failed to close bulk row iterator", e);
			}
		}
	}
}
