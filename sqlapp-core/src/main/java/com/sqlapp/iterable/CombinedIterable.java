/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import com.sqlapp.util.iterator.IteratorCloseUtils;

/**
 * Combined Iterable
 * 
 * @param <E>
 */
public class CombinedIterable<E> implements Iterable<E> {

	private final List<? extends Iterable<? extends E>> iterableList;

	private final Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer;

	public CombinedIterable(final List<? extends Iterable<? extends E>> iterableList,
			Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer) {
		this.iterableList = iterableList;
		this.switchConsumer = switchConsumer;
	}

	public CombinedIterable(final List<? extends Iterable<? extends E>> iterableList) {
		this.iterableList = iterableList;
		this.switchConsumer = itr -> {
		};
	}

	@Override
	public Iterator<E> iterator() {
		return new CombinedIterator<>(iterableList, switchConsumer);
	}

	static class CombinedIterator<E> implements Iterator<E>, AutoCloseable {

		private final Iterator<? extends Iterable<? extends E>> iterableIterator;
		private Iterator<? extends E> currentIterator = Collections.emptyIterator();
		private final Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer;
		private final Set<Iterator<?>> closedIterators = Collections.newSetFromMap(new IdentityHashMap<>());
		private boolean closed;

		CombinedIterator(final List<? extends Iterable<? extends E>> iterableList,
				Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer) {
			this.iterableIterator = iterableList.iterator();
			this.switchConsumer = switchConsumer;
		}

		@Override
		public boolean hasNext() {
			if (closed) {
				return false;
			}
			try {
				while (!currentIterator.hasNext()) {
					closeOne(null, currentIterator);
					if (!iterableIterator.hasNext()) {
						close();
						return false;
					}
					currentIterator = java.util.Objects.requireNonNull(iterableIterator.next(),
							"combined iterable").iterator();
					currentIterator = java.util.Objects.requireNonNull(currentIterator,
							"combined iterable returned a null iterator");
					switchConsumer.accept(iterableIterator);
				}
				return true;
			} catch (final RuntimeException | Error e) {
				closeOnFailure(e);
				throw e;
			}
		}

		@Override
		public E next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			try {
				return currentIterator.next();
			} catch (final RuntimeException | Error e) {
				closeOnFailure(e);
				throw e;
			}
		}

		@Override
		public void remove() {
			currentIterator.remove();
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			closeOne(null, currentIterator, iterableIterator);
		}

		private void closeOnFailure(final Throwable failure) {
			if (closed) {
				return;
			}
			closed = true;
			closeOne(failure, currentIterator, iterableIterator);
		}

		private void closeOne(final Throwable failure, final Iterator<?>... iterators) {
			final List<Iterator<?>> open = new java.util.ArrayList<>(iterators.length);
			for (final Iterator<?> iterator : iterators) {
				if (closedIterators.add(iterator)) {
					open.add(iterator);
				}
			}
			IteratorCloseUtils.close(failure, open.toArray(Iterator<?>[]::new));
		}
	}
}
