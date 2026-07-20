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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

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

	static class CombinedIterator<E> implements Iterator<E> {

		private final Iterator<? extends Iterable<? extends E>> iterableIterator;
		private Iterator<? extends E> currentIterator = Collections.emptyIterator();
		private final Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer;

		CombinedIterator(final List<? extends Iterable<? extends E>> iterableList,
				Consumer<Iterator<? extends Iterable<? extends E>>> switchConsumer) {
			this.iterableIterator = iterableList.iterator();
			this.switchConsumer = switchConsumer;
			switchConsumer.accept(iterableIterator);
		}

		@Override
		public boolean hasNext() {
			while (!currentIterator.hasNext()) {
				if (!iterableIterator.hasNext()) {
					return false;
				}
				currentIterator = iterableIterator.next().iterator();
				switchConsumer.accept(iterableIterator);
			}
			return true;
		}

		@Override
		public E next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			return currentIterator.next();
		}

		@Override
		public void remove() {
			currentIterator.remove();
		}
	}
}
