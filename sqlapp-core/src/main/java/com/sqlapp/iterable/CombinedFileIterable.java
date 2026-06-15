
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

package com.sqlapp.iterable;

import java.util.Iterator;
import java.util.List;

import com.sqlapp.util.FileUtils;

public class CombinedFileIterable<T> implements Iterable<T> {

	private final List<Iterable<T>> iterableList;

	public CombinedFileIterable(List<Iterable<T>> iterableList) {
		this.iterableList = iterableList;
	}

	@Override
	public Iterator<T> iterator() {
		return new CombinedFileIterator<T>(iterableList);
	}

	static class CombinedFileIterator<T> implements Iterator<T> {
		private final List<Iterable<T>> iterableList;
		private Iterator<T> iterator;
		private int i;

		public CombinedFileIterator(List<Iterable<T>> iterableList) {
			this.iterableList = iterableList;
		}

		private Iterator<T> currentIterator() {
			if (iterator != null) {
				return iterator;
			}
			if (i < iterableList.size()) {
				iterator = iterableList.get(i).iterator();
			} else {
				iterator = null;
			}
			return iterator;
		}

		private Iterator<T> nextIterator() {
			i++;
			iterator = null;
			return currentIterator();
		}

		@Override
		public boolean hasNext() {
			Iterator<T> iterator = currentIterator();
			if (iterator == null) {
				return false;
			}
			boolean hasNext = iterator.hasNext();
			if (hasNext) {
				return hasNext;
			}
			while (true) {
				iterator = nextIterator();
				if (iterator == null) {
					return false;
				}
				hasNext = iterator.hasNext();
				if (hasNext) {
					return hasNext;
				} else {
					FileUtils.close(iterator);
				}
			}
		}

		@Override
		public T next() {
			Iterator<T> iterator = currentIterator();
			if (iterator != null) {
				return iterator.next();
			}
			return null;
		}
	}
}
