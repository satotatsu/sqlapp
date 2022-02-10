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

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * CaseInsensitiveな重複を許すマップMap
 * 
 * @author SATOH
 *
 */
public class CaseInsensitiveGetMap<T> implements Map<String, T>, Serializable,
		Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1946073595454206975L;
	/**
	 * CaseInsensitveMapマップ
	 */
	protected CaseInsensitiveMap<T> caseInsensitiveMap = new CaseInsensitiveMap<T>();
	/**
	 * 全ての値を格納するマップ
	 */
	protected Map<String, T> map = new LinkedHashMap<String, T>();

	/**
	 * コンストラクタ
	 */
	public CaseInsensitiveGetMap() {
		caseInsensitiveMap = new CaseInsensitiveMap<T>();
		map = new LinkedHashMap<String, T>();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param size
	 */
	public CaseInsensitiveGetMap(final int size) {
		caseInsensitiveMap = new CaseInsensitiveMap<T>(size);
		map = new LinkedHashMap<String, T>(size);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public T put(final String key, final T value) {
		T obj=null;
		if (!map.containsKey(key)){
			obj=map.put(key, value);
			if (!caseInsensitiveMap.containsKey(key)){
				caseInsensitiveMap.put(key, value);
			}
			return obj;
		}
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		map.clear();
		caseInsensitiveMap.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(final Object key) {
		if (map.containsKey(key)){
			return true;
		}
		return caseInsensitiveMap.containsKey(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(final Object value) {
		if (map.containsValue(value)){
			return true;
		}
		return caseInsensitiveMap.containsValue(value);
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
		return get(key.toString());
	}

	public T get(final String key) {
		if (map.containsKey(key)){
			return map.get(key);
		}
		return caseInsensitiveMap.get(key);
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
		T obj=null;
		if (map.containsKey(key)){
			obj= map.remove(key);
			caseInsensitiveMap.remove(key);
		}
		return obj;
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
	public CaseInsensitiveGetMap<T> clone() {
		CaseInsensitiveGetMap<T> clone=new CaseInsensitiveGetMap<T>();
		clone.caseInsensitiveMap=this.caseInsensitiveMap.clone();
		clone.map.putAll(this.map);
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
		if (!(obj instanceof CaseInsensitiveGetMap)) {
			return false;
		}
		CaseInsensitiveGetMap<?> cst = (CaseInsensitiveGetMap<?>) obj;
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
