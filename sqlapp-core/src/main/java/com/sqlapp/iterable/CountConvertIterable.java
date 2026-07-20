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

import java.io.Closeable;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.sqlapp.util.FileUtils;

/**
 * 指定した回数だけ値を返すIterable
 * 
 * @param <E>
 */
public class CountConvertIterable<E, F> implements Iterable<F> {

	private final Consumer<Iterator<E>> initializer;

	private final BiFunction<Long, E, F> valueConverter;

	private final Iterable<E> iterable;

	public CountConvertIterable(final Consumer<Iterator<E>> initializer, final Iterable<E> iterable,
			final BiFunction<Long, E, F> valueConverter) {
		this.initializer = initializer;
		this.iterable = iterable;
		this.valueConverter = valueConverter;
	}

	public CountConvertIterable(final Iterable<E> iterable, final BiFunction<Long, E, F> valueConverter) {
		this.initializer = itr -> {
		};
		this.iterable = iterable;
		this.valueConverter = valueConverter;
	}

	@Override
	public Iterator<F> iterator() {
		final Iterator<E> itr = iterable.iterator();
		initializer.accept(itr);
		return new CountConvertIterator<E, F>(itr, valueConverter);
	}

	static class CountConvertIterator<E, F> implements Iterator<F>, AutoCloseable, Closeable {

		private final BiFunction<Long, E, F> valueConverter;
		private final Iterator<E> iterator;

		public CountConvertIterator(final Iterator<E> iterator, final BiFunction<Long, E, F> valueConverter) {
			this.iterator = iterator;
			this.valueConverter = valueConverter;
		}

		private long count = -1;

		@Override
		public boolean hasNext() {
			boolean bool = iterator.hasNext();
			if (bool) {
				count++;
				return bool;
			}
			close();
			return bool;
		}

		@Override
		public F next() {
			E val = iterator.next();
			F converted = valueConverter.apply(count, val);
			return converted;
		}

		@Override
		public void close() {
			FileUtils.close(iterator);
		}
	}
}
