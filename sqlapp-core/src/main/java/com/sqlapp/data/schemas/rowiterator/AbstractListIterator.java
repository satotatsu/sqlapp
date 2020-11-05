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

import java.util.ListIterator;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.function.RowValueConverter;

public abstract class AbstractListIterator implements ListIterator<Row>, AutoCloseable {
	private final RowValueConverter rowValueConverter;

	protected AbstractListIterator(){
		this.rowValueConverter=(r,c,v)->v;
	}

	protected AbstractListIterator(final RowValueConverter rowValueConverter){
		this.rowValueConverter=rowValueConverter;
	}

	
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

