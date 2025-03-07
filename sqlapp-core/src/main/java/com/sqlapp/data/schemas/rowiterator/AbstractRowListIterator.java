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

import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.RowValueConverter;

public abstract class AbstractRowListIterator<T> extends AbstractListIterator {

	protected AbstractRowListIterator(RowCollection c, long index, RowValueConverter rowValueConverter){
		super(rowValueConverter);
		this.table=c.getParent();
		this.index=index;
	}

	protected final Table table;
	protected long index = 0;		

	protected long count = 0;
	private long limit = Long.MAX_VALUE;
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
		} catch (Exception e) {
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
		Row row = this.table.newRow();
		try {
			T t=this.read();
			set(t, row);
			return row;
		} catch (RuntimeException e) {
			closeSilent();
			throw e;
		} catch (Exception e) {
			closeSilent();
			throw new RuntimeException(e);
		} finally {
			if (!hasNext) {
				closeSilent();
			}
		}
	}
	
	protected abstract void doClose();
	
	protected void put(Row row, Column column, Object value){
		if (column.getDataType()!=null&&column.getDataType().isBinary()&&value instanceof String){
			SchemaUtils.putDialect(row, column, this.getRowValueConverter().apply(row, column, value));
		} else{
			row.put(column, this.getRowValueConverter().apply(row, column, value));
		}
	}
	
	protected void closeSilent(){
		try {
			close();
		} catch (Exception e) {
		}
	}
	
	@Override
	public void close() throws Exception{
		if (!dispose){
			this.doClose();
			dispose=true;
		}
	}
	
	protected long getTypeLength(String value){
		return DialectUtils.getDefaultTypeLength(value);
	}
}

