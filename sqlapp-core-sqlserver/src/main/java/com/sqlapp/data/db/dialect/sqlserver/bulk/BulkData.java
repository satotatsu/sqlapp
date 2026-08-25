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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.CommonUtils;

/** Streams a sqlapp Table's rows to the Microsoft JDBC bulk-copy API. */
public class BulkData implements ISQLServerBulkData, AutoCloseable {
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
			columns.add(column);
			columnOrdinals.add(columns.size());
		}
		this.rows = table.getRows().iterator();
	}

	public static SQLServerBulkCopyOptions toSqlServerOptions(final BulkOption options)
			throws SQLServerException {
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
		if (column.getDataType() == null || column.getDataType().getJdbcType() == null) {
			return java.sql.Types.OTHER;
		}
		return column.getDataType().getJdbcType().getVendorTypeNumber();
	}

	@Override
	public int getPrecision(final int ordinal) {
		final Long length = getColumn(ordinal).getLength();
		return length == null ? 0 : Math.toIntExact(length);
	}

	@Override
	public int getScale(final int ordinal) {
		final Integer scale = getColumn(ordinal).getScale();
		return scale == null ? 0 : scale;
	}

	@Override
	public Object[] getRowData() throws SQLException {
		if (currentRow == null) {
			throw new SQLException("next() must return true before getRowData()");
		}
		final Object[] values = new Object[columns.size()];
		for (int i = 0; i < columns.size(); i++) {
			values[i] = currentRow.get(columns.get(i).getOrdinal());
		}
		return values;
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
