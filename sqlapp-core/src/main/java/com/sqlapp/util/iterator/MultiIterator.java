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

import java.io.Closeable;
import java.util.Iterator;
import java.util.function.Function;

import com.sqlapp.util.FileUtils;

public class MultiIterator<S, T> implements Iterator<T> {

	private Iterator<S> iterator;

	private Iterator<T> current;

	private Function<S, Iterator<T>> converter;

	public MultiIterator(Iterable<S> iterable, Function<S, Iterator<T>> converter) {
		this.iterator = iterable.iterator();
		this.current = null;
		this.converter = converter;
	}

	public MultiIterator(Iterator<S> itarator, Function<S, Iterator<T>> converter) {
		this.iterator = itarator;
		this.current = null;
		this.converter = converter;
	}

	@Override
	public boolean hasNext() {
		boolean bool;
		if (current == null) {
			while (iterator.hasNext()) {
				current = converter.apply(iterator.next());
				bool = current.hasNext();
				if (bool) {
					return bool;
				} else {
					closeInternal(current);
				}
			}
		} else {
			bool = current.hasNext();
			if (bool) {
				return bool;
			}
			while (iterator.hasNext()) {
				current = converter.apply(iterator.next());
				bool = current.hasNext();
				if (bool) {
					return bool;
				} else {
					closeInternal(current);
				}
			}
		}
		closeInternal(iterator);
		return false;
	}

	@Override
	public T next() {
		return current.next();
	}

	private void closeInternal(Iterator<?> itr) {
		if (itr instanceof Closeable) {
			FileUtils.close((Closeable) itr);
		} else if (itr instanceof AutoCloseable) {
			FileUtils.close((AutoCloseable) itr);
		}
	}
}
