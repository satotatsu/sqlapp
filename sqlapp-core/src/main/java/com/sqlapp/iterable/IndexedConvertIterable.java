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
/**
 * インデックスを付与して要素を変換するIterable
 *
 * @param <E> 入力要素
 * @param <F> 変換後要素
 */
public class IndexedConvertIterable<E, F> implements Iterable<F> {

	private final Consumer<Iterator<? extends E>> initializer;

	private final BiFunction<Long, ? super E, ? extends F> valueConverter;

	private final Iterable<? extends E> iterable;

	public IndexedConvertIterable(final Consumer<Iterator<? extends E>> initializer, final Iterable<? extends E> iterable,
			final BiFunction<Long, ? super E, ? extends F> valueConverter) {
		this.initializer = initializer;
		this.iterable = iterable;
		this.valueConverter = valueConverter;
	}

	public IndexedConvertIterable(final Iterable<? extends E> iterable,
			final BiFunction<Long, ? super E, ? extends F> valueConverter) {
		this(itr -> {
		}, iterable, valueConverter);
	}

	@Override
	public Iterator<F> iterator() {
		final Iterator<? extends E> itr = iterable.iterator();
		initializer.accept(itr);
		return new CountConvertIterator<>(itr, valueConverter);
	}

	static class CountConvertIterator<E, F> implements Iterator<F>, AutoCloseable, Closeable {

		private final Iterator<? extends E> iterator;

		private final BiFunction<Long, ? super E, ? extends F> valueConverter;

		private long count = 0;

		private boolean closed = false;

		CountConvertIterator(final Iterator<? extends E> iterator,
				final BiFunction<Long, ? super E, ? extends F> valueConverter) {
			this.iterator = iterator;
			this.valueConverter = valueConverter;
		}

		@Override
		public boolean hasNext() {
			final boolean hasNext = iterator.hasNext();
			if (!hasNext) {
				close();
			}
			return hasNext;
		}

		@Override
		public F next() {
			return valueConverter.apply(count++, iterator.next());
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				FileUtils.close(iterator);
			}
		}
	}
}