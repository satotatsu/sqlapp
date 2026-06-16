/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.util.iterator;

import java.util.Iterator;
import java.util.ListIterator;

public class ListIteratorWrapper<T> extends AutoCloseIterator<T> implements ListIterator<T> {

	public ListIteratorWrapper(Iterator<T> iterator) {
		super(iterator);
	}

	@Override
	public boolean hasPrevious() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public T previous() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public int nextIndex() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public int previousIndex() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void set(T e) {
		throw new UnsupportedOperationException("hasPrevious");
	}

	@Override
	public void add(T e) {
		throw new UnsupportedOperationException("hasPrevious");
	}
}
