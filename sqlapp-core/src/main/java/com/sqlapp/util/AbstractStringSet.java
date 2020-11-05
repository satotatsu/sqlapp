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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.set;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public abstract class AbstractStringSet implements Set<String>, Serializable,
		Cloneable {

	/** serialVersionUID */
	private static final long serialVersionUID = 610779830671683832L;

	protected Set<String> inner = null;

	/**
	 * デフォルトコンストラクタ
	 */
	public AbstractStringSet() {
		inner = set();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 */
	public AbstractStringSet(final int capacity) {
		inner = set(capacity);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 * @param loadFactor
	 */
	public AbstractStringSet(final int capacity, final float loadFactor) {
		inner = set(capacity, loadFactor);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param baseSet
	 *            元になるSet
	 */
	public AbstractStringSet(final Set<String> baseSet) {
		inner = baseSet;
	}

	protected abstract String convertKey(String key);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#size()
	 */
	@Override
	public int size() {
		return inner.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return inner.isEmpty();
	}

	public boolean contains(String o) {
		return inner.contains(convertKey(o));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#contains(java.lang.Object)
	 */
	@Override
	public boolean contains(Object o) {
		return inner.contains(convertKey((String) o));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#iterator()
	 */
	@Override
	public Iterator<String> iterator() {
		return inner.iterator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#toArray()
	 */
	@Override
	public Object[] toArray() {
		return inner.toArray();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#toArray(T[])
	 */
	@Override
	public <T> T[] toArray(T[] a) {
		return inner.toArray(a);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#add(java.lang.Object)
	 */
	@Override
	public boolean add(String e) {
		return inner.add(convertKey(e));
	}

	public boolean remove(String o) {
		return inner.remove(convertKey(o));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object o) {
		return inner.remove(convertKey((String) o));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#containsAll(java.util.Collection)
	 */
	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object val : c) {
			if (!contains(val)) {
				return false;
			}
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends String> c) {
		for (String val : c) {
			add(val);
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#retainAll(java.util.Collection)
	 */
	@Override
	public boolean retainAll(Collection<?> c) {
		List<String> list = list();
		for (Object val : c) {
			list.add(convertKey((String) val));
		}
		return inner.retainAll(list);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#removeAll(java.util.Collection)
	 */
	@Override
	public boolean removeAll(Collection<?> c) {
		List<String> list = list();
		for (Object val : c) {
			list.add(convertKey((String) val));
		}
		return inner.removeAll(list);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return inner.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#clear()
	 */
	@Override
	public void clear() {
		inner.clear();
	}

	public abstract AbstractStringSet newInstance();

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
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AbstractStringSet)) {
			return false;
		}
		// AbstractStringSet val=cast(obj);
		if (!this.inner.equals(obj)) {
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
		return this.inner.hashCode();
	}
}
