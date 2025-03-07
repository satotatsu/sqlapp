/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas.rowiterator;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.DefaultPredicate;

/**
 * JDBCで行の値を動的に取得するためのイテレーターのハンドラー
 * 
 * @author tatsuo satoh
 * 
 */
public class ResultSetRowIteratorHandler extends AbstractRowIteratorHandler {

	private final ResultSet resultSet;

	private Predicate<RowCollection> filter;

	public ResultSetRowIteratorHandler(final ResultSet resultSet, final RowValueConverter valueConverter) {
		super(valueConverter);
		this.resultSet=resultSet;
		this.filter=new DefaultPredicate<RowCollection>();
	}

	public ResultSetRowIteratorHandler(final ResultSet resultSet, final Predicate<RowCollection> filter, final RowValueConverter valueConverter) {
		super(valueConverter);
			this.resultSet=resultSet;
		this.filter=filter;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandler#iterator(com.sqlapp.data.schemas
	 * .RowCollection)
	 */
	@Override
	public Iterator<Row> iterator(final RowCollection rows) {
		if (getFilter().test(rows)) {
			final ResultSetIterator iterator = getResultSetIterator(rows, resultSet, 0);
			return iterator;
		} else {
			final List<Row> list = CommonUtils.emptyList();
			return list.iterator();
		}
	}

	protected ResultSetIterator getResultSetIterator(final RowCollection rows,final ResultSet resultSet,
			final int index) {
		final ResultSetIterator iterator = new ResultSetIterator(rows,
				resultSet, index, this.getRowValueConverter());
		return iterator;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandler#listIterator(com.sqlapp.data
	 * .schemas.RowCollection, int)
	 */
	@Override
	public ListIterator<Row> listIterator(final RowCollection rows, final int index) {
		if (getFilter().test(rows)) {
			final ResultSetIterator iterator = getResultSetIterator(rows, resultSet, index);
			return iterator;
		} else {
			final List<Row> list = CommonUtils.emptyList();
			return list.listIterator();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandler#listIterator(com.sqlapp.data
	 * .schemas.RowCollection)
	 */
	@Override
	public ListIterator<Row> listIterator(final RowCollection rows) {
		if (getFilter().test(rows)) {
			final ResultSetIterator iterator = getResultSetIterator(rows, resultSet, 0);
			return iterator;
		} else {
			final List<Row> list = CommonUtils.emptyList();
			return list.listIterator();
		}
	}

	/**
	 * @return the filter
	 */
	public Predicate<RowCollection> getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            the filter to set
	 */
	public void setFilter(final Predicate<RowCollection> filter) {
		this.filter = filter;
	}

	/**
	 * ResultSetIteratorの実装クラス
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public static class ResultSetIterator extends AbstractRowListIterator<ResultSet> {
		private final List<ColumnPosition> columnList = CommonUtils.list();
		private ResultSet resultSet;
		private Dialect dialect;

		public ResultSetIterator(final RowCollection rows,
				final ResultSet resultSet, final int index, final RowValueConverter valueConverter) {
			super(rows, index, valueConverter);
			this.resultSet = resultSet;
		}

		@Override
		protected void preInitialize() throws Exception {
		}

		@Override
		protected void initializeColumn() throws Exception {
			final ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
			for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
				final String label = resultSetMetaData.getColumnLabel(i);
				final String name = resultSetMetaData.getColumnName(i);
				Column column=searchColumn(table, label);
				if (column == null) {
					column=searchColumn(table, name);
				}
				if (column == null) {
					continue;
				}
				final DbDataType<?> type = dialect.getDbDataType(column);
				if (type==null){
					throw new NullPointerException("type is null. column="+column);
				}
				final ColumnPosition columnPosition = new ColumnPosition(i, column,
						type.getJdbcTypeHandler());
				columnList.add(columnPosition);
			}
		}

		@Override
		protected ResultSet read() throws Exception {
			return resultSet;
		}

		@Override
		protected boolean hasNextInternal() throws Exception {
			return resultSet.next();
		}

		protected ResultSet createResultSet() throws SQLException {
			resultSet.setFetchSize(1000);
			return resultSet;
		}

		@Override
		protected void doClose() {
			DbUtils.close(resultSet);
			this.resultSet = null;
		}

		@Override
		protected void set(final ResultSet val, final Row row) throws Exception {
			final int size = columnList.size();
			for (int i = 0; i < size; i++) {
				final ColumnPosition columnPosition = columnList.get(i);
				final Column column = columnPosition.column;
				final Object obj = columnPosition.jdbcTypeHandler.getObject(
						resultSet, columnPosition.index);
				row.put(column.getOrdinal(), obj);
			}
		}
	}

	static class ColumnPosition {
		public final int index;
		public final Column column;
		public final JdbcTypeHandler jdbcTypeHandler;

		ColumnPosition(final int index, final Column column, final JdbcTypeHandler jdbcTypeHandler) {
			this.index = index;
			this.column = column;
			this.jdbcTypeHandler = jdbcTypeHandler;
		}
	}

}
