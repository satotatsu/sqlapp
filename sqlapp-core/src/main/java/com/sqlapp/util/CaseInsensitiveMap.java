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
import static com.sqlapp.util.CommonUtils.upperMap;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * CaseInsensitiveなMap
 * 
 * @author SATOH
 *
 */
public class CaseInsensitiveMap<T> implements Map<String, T>, Serializable,
		Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1946073595454206975L;

	protected Map<String, T> map = null;
	/**
	 * 大文字変換後のキー：変換前のキー
	 */
	protected Map<String, String> keyMap = null;

	/**
	 * コンストラクタ
	 */
	public CaseInsensitiveMap() {
		map = map();
		keyMap = upperMap();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param size
	 */
	public CaseInsensitiveMap(final int size) {
		map = map(size);
		keyMap = upperMap(size);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param baseMap
	 *            元になるマップ
	 */
	public CaseInsensitiveMap(final Map<String, T> baseMap) {
		map = baseMap;
		keyMap = upperMap(baseMap.size());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public T put(final String key, final T value) {
		if (!keyMap.containsKey(key)) {
			keyMap.put(key, key);
		}
		return map.put(keyMap.get(key), value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		map.clear();
		keyMap.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(final Object key) {
		return keyMap.containsKey(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(final Object value) {
		return map.containsValue(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public Set<Map.Entry<String, T>> entrySet() {
		return map.entrySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public T get(final Object key) {
		if (key==null){
			return get((String)null);
		}
		return get(key.toString());
	}

	public T get(final String key) {
		T obj = map.get(key);
		if (obj != null) {
			return obj;
		}
		String innerKey=keyMap.get(key);
		if (innerKey==null){
			return null;
		}
		return map.get(innerKey);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#keySet()
	 */
	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	@Override
	public T remove(final Object key) {
		String originalKey = keyMap.get(key);
		T ret;
		if (originalKey!=null){
			ret = map.remove(originalKey);
			keyMap.remove(originalKey);
		} else{
			ret = map.remove(key);
		}
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#size()
	 */
	@Override
	public int size() {
		return map.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#values()
	 */
	@Override
	public Collection<T> values() {
		return map.values();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@Override
	public void putAll(final Map<? extends String, ? extends T> m) {
		for (Map.Entry<? extends String, ? extends T> entry : m.entrySet()) {
			String key = entry.getKey();
			T value = entry.getValue();
			put(key, value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public CaseInsensitiveMap<T> clone() {
		CaseInsensitiveMap<T> clone=new CaseInsensitiveMap<T>(CommonUtils.cloneMap(this.map));
		return clone;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return map.toString();
	}

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
		if (!(obj instanceof CaseInsensitiveMap)) {
			return false;
		}
		CaseInsensitiveMap<?> cst = (CaseInsensitiveMap<?>) obj;
		if (!CommonUtils.eq(this.map, cst.map)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.map.hashCode();
	}
}
