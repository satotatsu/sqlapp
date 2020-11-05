/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.map;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * キーの変換機能を持つマップ
 * 
 * @author satoh
 *
 * @param <T>
 */
public abstract class AbstractStringMap<T> implements Map<String, T>,
		Serializable, Cloneable {

	/** serialVersionUID */
	private static final long serialVersionUID = -602530703582075583L;

	protected Map<String, T> inner = null;

	/**
	 * デフォルトコンストラクタ
	 */
	public AbstractStringMap() {
		inner = map();
	}

	/**
	 * デフォルトコンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 */
	public AbstractStringMap(final int capacity) {
		inner = map(capacity);
	}

	/**
	 * デフォルトコンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 * @param loadFactor
	 */
	public AbstractStringMap(final int capacity, final float loadFactor) {
		inner = map(capacity);
	}

	/**
	 * デフォルトコンストラクタ
	 * 
	 * @param baseMap
	 *            元になるマップ
	 */
	public AbstractStringMap(final Map<String, T> baseMap) {
		inner = baseMap;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#size()
	 */
	@Override
	public int size() {
		return inner.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return inner.isEmpty();
	}

	/**
	 * キーの変換
	 * 
	 * @param key
	 */
	protected abstract String convertKey(String key);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(Object key) {
		return inner.containsKey(convertKey((String) key));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(Object value) {
		return inner.containsValue(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public T get(Object key) {
		return inner.get(convertKey((String) key));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public T put(String key, T value) {
		return inner.put(convertKey((String) key), value);
	}

	/**
	 * @param key
	 */
	public T remove(String key) {
		return inner.remove(convertKey(key));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	@Override
	public T remove(Object key) {
		return inner.remove(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void putAll(Map<? extends String, ? extends T> m) {
		for (Map.Entry<?, ?> entry : m.entrySet()) {
			put((String) entry.getKey(), (T) entry.getValue());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		inner.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#keySet()
	 */
	@Override
	public Set<String> keySet() {
		return inner.keySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#values()
	 */
	@Override
	public Collection<T> values() {
		return inner.values();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public Set<java.util.Map.Entry<String, T>> entrySet() {
		return inner.entrySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return inner.toString();
	}

	/**
	 * 自分自身を元に新しい空のインスタンスを作成
	 * 
	 */
	public abstract AbstractStringMap<T> newInstance();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Map)) {
			return false;
		}
		return this.inner.equals(obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.inner.hashCode();
	}
}
