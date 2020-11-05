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

import java.util.Map;
import java.util.Set;

/**
 * CaseInsensitiveなSet
 * 
 * @author SATOH
 *
 */
public class CaseInsensitiveSet extends AbstractStringSet {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1946073595454206975L;

	private Map<String, String> keyMap = CommonUtils.upperLinkedMap();

	/**
	 * コンストラクタ
	 */
	public CaseInsensitiveSet() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 */
	public CaseInsensitiveSet(final int capacity) {
		super(capacity);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param capacity
	 *            マップの初期サイズ
	 */
	public CaseInsensitiveSet(final int capacity, final float loadFactor) {
		super(capacity, loadFactor);
	}

	/**
	 * デフォルトコンストラクタ
	 * 
	 * @param baseSet
	 *            元になるSet
	 */
	public CaseInsensitiveSet(final Set<String> baseSet) {
		super(baseSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractStringSet#add(java.lang.String)
	 */
	@Override
	public boolean add(String e) {
		keyMap.putIfAbsent(e, e);
		return super.add(e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractStringSet#convertKey(java.lang.String)
	 */
	@Override
	protected String convertKey(String key) {
		return keyMap.get(key);
	}

	public boolean remove(String o) {
		boolean bool = super.remove(convertKey(o));
		keyMap.remove(o);
		return bool;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Set#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object o) {
		boolean bool = super.remove(convertKey((String) o));
		keyMap.remove(o);
		return bool;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractStringSet#newInstance()
	 */
	@Override
	public AbstractStringSet newInstance() {
		return new CaseInsensitiveSet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractStringSet#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		keyMap.clear();
	}

}
