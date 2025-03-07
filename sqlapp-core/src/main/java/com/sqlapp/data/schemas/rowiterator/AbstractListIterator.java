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

import java.util.ListIterator;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractListIterator implements ListIterator<Row>, AutoCloseable {
	private final RowValueConverter rowValueConverter;

	protected AbstractListIterator(){
		this.rowValueConverter=(r,c,v)->v;
	}

	protected AbstractListIterator(final RowValueConverter rowValueConverter){
		this.rowValueConverter=rowValueConverter;
	}
	
	protected Column searchColumn(final Table table, final String columnName) {
		if (columnName==null) {
			return null;
		}
		
		Column column=columnNameCache.get(columnName);
		if (column!=null){
			return column;
		}
		column=table.getColumns().get(columnName);
		if (column!=null){
			columnNameCache.put(columnName, column);
			return column;
		}
		column=table.getColumns().get(columnName.toLowerCase());
		if (column!=null){
			columnNameCache.put(columnName, column);
			return column;
		}
		column=table.getColumns().get(columnName.toUpperCase());
		if (column!=null){
			columnNameCache.put(columnName, column);
			return column;
		}
		final String replaceName=columnName.replace("_", "");
		for(final Column cols:table.getColumns()) {
			if(CommonUtils.eqIgnoreCase(replaceName, cols.getName().replace("_", ""))) {
				columnNameCache.put(columnName, cols);
				return cols;
			}
		}
		return null;
	}

	private final Map<String, Column> columnNameCache=CommonUtils.map();
	
	@Override
	public boolean hasPrevious() {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support hasPrevious.");
	}

	@Override
	public Row previous() {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support previous.");
	}

	@Override
	public int nextIndex() {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support nextIndex.");
	}

	@Override
	public int previousIndex() {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support previousIndex.");
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support remove.");
	}

	@Override
	public void set(final Row e) {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support set.");
	}

	@Override
	public void add(final Row e) {
		throw new UnsupportedOperationException(this.getClass()
				.getSimpleName() + " does not support add.");
	}

	/**
	 * @return the valueConverter
	 */
	protected RowValueConverter getRowValueConverter() {
		return rowValueConverter;
	}

}

