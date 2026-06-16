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
