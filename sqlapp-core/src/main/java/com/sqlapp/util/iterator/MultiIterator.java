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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class MultiIterator<S, T> implements Iterator<T>, AutoCloseable {

	private Iterator<S> iterator;

	private Iterator<T> current;

	private Function<S, Iterator<T>> converter;
	private final Set<Iterator<?>> closedIterators = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean closed;

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
		if (closed) {
			return false;
		}
		try {
			while (true) {
				if (current != null) {
					if (current.hasNext()) {
						return true;
					}
					closeOne(null, current);
					current = null;
				}
				if (!iterator.hasNext()) {
					close();
					return false;
				}
				current = Objects.requireNonNull(converter.apply(iterator.next()),
						"converter returned a null iterator");
			}
		} catch (final RuntimeException | Error e) {
			closeOnFailure(e);
			throw e;
		}
	}

	@Override
	public T next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		try {
			return current.next();
		} catch (final RuntimeException | Error e) {
			closeOnFailure(e);
			throw e;
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		closeOne(null, current, iterator);
	}

	private void closeOnFailure(final Throwable failure) {
		if (closed) {
			return;
		}
		closed = true;
		closeOne(failure, current, iterator);
	}

	private void closeOne(final Throwable failure, final Iterator<?>... iterators) {
		final List<Iterator<?>> open = new java.util.ArrayList<>(iterators.length);
		for (final Iterator<?> candidate : iterators) {
			if (candidate != null && closedIterators.add(candidate)) {
				open.add(candidate);
			}
		}
		IteratorCloseUtils.close(failure, open.toArray(Iterator<?>[]::new));
	}
}
