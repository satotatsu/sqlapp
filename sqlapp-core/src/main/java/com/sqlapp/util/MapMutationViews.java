package com.sqlapp.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Backed map views that notify the owner after each successful mutation. */
final class MapMutationViews {

	private MapMutationViews() {
	}

	static <K, V> Map<K, V> of(Map<K, V> backing, Runnable changed) {
		return new AbstractMap<K, V>() {
			@Override
			public boolean containsKey(Object key) {
				return backing.containsKey(key);
			}

			@Override
			public boolean containsValue(Object value) {
				return backing.containsValue(value);
			}

			@Override
			public Set<Entry<K, V>> entrySet() {
				return new AbstractSet<Entry<K, V>>() {
					@Override
					public void clear() {
						backing.clear();
						changed.run();
					}

					@Override
					public int size() {
						return backing.size();
					}

					@Override
					public Iterator<Entry<K, V>> iterator() {
						Iterator<Entry<K, V>> iterator = backing.entrySet().iterator();
						return new Iterator<Entry<K, V>>() {
							@Override
							public boolean hasNext() {
								return iterator.hasNext();
							}

							@Override
							public Entry<K, V> next() {
								Entry<K, V> entry = iterator.next();
								return new Entry<K, V>() {
									@Override
									public K getKey() {
										return entry.getKey();
									}

									@Override
									public V getValue() {
										return entry.getValue();
									}

									@Override
									public V setValue(V value) {
										V previous = entry.setValue(value);
										changed.run();
										return previous;
									}

									@Override
									public boolean equals(Object other) {
										return other instanceof Entry<?, ?> candidate
												&& Objects.equals(getKey(), candidate.getKey())
												&& Objects.equals(getValue(), candidate.getValue());
									}

									@Override
									public int hashCode() {
										return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
									}

									@Override
									public String toString() {
										return getKey() + "=" + getValue();
									}
								};
							}

							@Override
							public void remove() {
								iterator.remove();
								changed.run();
							}
						};
					}
				};
			}
		};
	}
}
