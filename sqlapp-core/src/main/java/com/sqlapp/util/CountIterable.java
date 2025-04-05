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

package com.sqlapp.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * 指定した回数だけ値を返すIterable
 * 
 * @param <E>
 */
public class CountIterable<E> implements Iterable<E> {

	private final long start;;
	private final long limit;

	private final Function<Long, E> valueSupplier;

	public CountIterable(final long limit, final Function<Long, E> valueSupplier) {
		this(0, limit, valueSupplier);
	}

	public CountIterable(final long start, final long limit, final Function<Long, E> valueSupplier) {
		this.start = start;
		this.limit = limit;
		this.valueSupplier = valueSupplier;
	}

	@Override
	public Iterator<E> iterator() {
		return new CountIterator<E>(start, limit, valueSupplier);
	}

}
