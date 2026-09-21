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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class MultiIterable<S, T> implements Iterable<T> {

	private final List<S> iterable;
	private final MultiIterator<S, T> iterator;
	private final Function<S, Iterator<T>> converter;

	public MultiIterable(List<S> iterable, Function<S, Iterator<T>> converter) {
		this.iterable = Objects.requireNonNull(iterable, "iterable");
		this.iterator = null;
		this.converter = Objects.requireNonNull(converter, "converter");
	}

	public MultiIterable(Iterator<S> iterator, Function<S, Iterator<T>> converter) {
		this.iterable = null;
		this.converter = Objects.requireNonNull(converter, "converter");
		this.iterator = new MultiIterator<S, T>(Objects.requireNonNull(iterator, "iterator"), this.converter);
	}

	@Override
	public Iterator<T> iterator() {
		return iterable == null ? iterator : new MultiIterator<>(iterable, converter);
	}
}
