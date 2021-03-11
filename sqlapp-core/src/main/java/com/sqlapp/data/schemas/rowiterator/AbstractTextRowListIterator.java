/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas.rowiterator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.RowValueConverter;

public abstract class AbstractTextRowListIterator<T> extends AbstractListIterator {

	protected AbstractTextRowListIterator(final RowCollection c, final long index, final RowValueConverter rowValueConverter){
		super(rowValueConverter);
		this.table=c.getParent();
		this.index=index;
	}

	protected final Table table;
	protected long index = 0;		

	protected long count = 0;
	private final long limit = Long.MAX_VALUE;
	private boolean init=false;
	
	private boolean hasNext=false;
	
	private boolean dispose=false;
	
	private void initialize() throws Exception{
		if (init){
			return;
		}
		preInitialize();
		initializeColumn();
		long i = 0;
		while (i < index) {
			if (hasNextInternal()){
				read();
			} else{
				break;
			}
			i++;
		}
		this.init=true;
	}

	protected abstract void preInitialize() throws Exception;

	protected abstract void initializeColumn() throws Exception;

	protected abstract T read() throws Exception;

	@Override
	public boolean hasNext() {
		try {
			initialize();
			if (count >= limit) {
				hasNext = false;
				return hasNext;
			}
			hasNext=hasNextInternal();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
		if (!hasNext){
			closeSilent();
		} else{
			count++;
		}
		return hasNext;
	}

	protected abstract boolean hasNextInternal() throws Exception;

	protected abstract void set(T val, Row row) throws Exception;

	
	@Override
	public Row next() {
		final Row row = this.table.newRow();
		try {
			final T t=this.read();
			set(t, row);
			return row;
		} catch (final RuntimeException e) {
			closeSilent();
			throw e;
		} catch (final Exception e) {
			closeSilent();
			throw new RuntimeException(e);
		} finally {
			if (!hasNext) {
				closeSilent();
			}
		}
	}
	
	protected abstract void doClose();
	
	protected void put(final Row row, final Column column, final Object value){
		if (column.getDataType()!=null&&column.getDataType().isBinary()&&value instanceof String){
			SchemaUtils.putDialect(row, column, this.getRowValueConverter().apply(row, column, value));
		} else{
			row.put(column, this.getRowValueConverter().apply(row, column, value));
		}
	}
	
	protected void closeSilent(){
		try {
			close();
		} catch (final Exception e) {
		}
	}
	
	@Override
	public void close() throws Exception{
		if (!dispose){
			this.doClose();
			dispose=true;
		}
	}
	

	private static final Pattern NUMERIC_PATTERN=Pattern.compile("[-+]?[0-9]+");

	private static final Pattern FLOAT_PATTERN=Pattern.compile("^[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][-+]?[0-9]+)?$");

	protected DataType getDataType(final String text){
		if (text==null){
			return null;
		}
		if ("true".endsWith(text)||"false".endsWith(text)){
			return DataType.BOOLEAN;
		}
		Matcher matcher=NUMERIC_PATTERN.matcher(text);
		if (matcher.matches()){
			return DataType.BIGINT;
		}
		matcher=FLOAT_PATTERN.matcher(text);
		if (matcher.matches()){
			return DataType.DOUBLE;
		}
		return DataType.NVARCHAR;
	}
	
	protected long getTypeLength(final String value){
		return DialectUtils.getDefaultTypeLength(value);
	}
}

