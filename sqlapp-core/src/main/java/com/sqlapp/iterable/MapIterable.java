/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.iterable;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Map;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class MapIterable implements Iterable<Map<String, Object>> {

	private final Iterable<?> iterable;
	private final Iterator<?> iterator;
	private final String key;

	public MapIterable(String key, Iterator<?> iterator) {
		this.key = key.intern();
		this.iterable = null;
		this.iterator = iterator;
	}

	public MapIterable(String key, Iterable<?> iterable) {
		this.key = key.intern();
		this.iterable = iterable;
		this.iterator = null;
	}

	public MapIterable(Iterable<?> iterable) {
		this("value", iterable);
	}

	public MapIterable(Iterator<?> iterator) {
		this("value", iterator);
	}

	static class IteratorWrapper implements Iterator<Map<String, Object>>, AutoCloseable, Closeable {
		private final String key;
		private final Iterator<?> iterator;

		public IteratorWrapper(String key, Iterator<?> iterator) {
			this.key = key;
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Map<String, Object> next() {
			final Object obj = iterator.next();
			Map<String, Object> map = CommonUtils.map();
			map.put(key, obj);
			return map;
		}

		@Override
		public void close() {
			if (iterator instanceof Closeable) {
				FileUtils.close((Closeable) iterator);
			} else if (iterator instanceof AutoCloseable) {
				FileUtils.close((AutoCloseable) iterator);
			}
		}
	}

	@Override
	public Iterator<Map<String, Object>> iterator() {
		if (iterable != null) {
			return new IteratorWrapper(key, iterable.iterator());
		}
		return new IteratorWrapper(this.key, this.iterator);
	}
}
