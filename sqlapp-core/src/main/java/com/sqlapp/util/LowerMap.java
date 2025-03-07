/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static com.sqlapp.util.CommonUtils.*;

import java.util.Map;
/**
 * 小文字格納のマップ
 * @author satoh
 *
 * @param <T>
 */
public final class LowerMap<T> extends AbstractStringMap<T>{

	/** serialVersionUID */
	private static final long serialVersionUID = -5209759501954069043L;

	/**
	 * デフォルトコンストラクタ
	 */
	public LowerMap(){
	}

	/**
	 * コンストラクタ
	 * @param capacity マップの初期サイズ
	 */
	public LowerMap(final int capacity){
		super(capacity);
	}

	/**
	 * コンストラクタ
	 * @param capacity マップの初期サイズ
	 */
	public LowerMap(final int capacity, final float loadFactor){
		super(capacity, loadFactor);
	}

	/**
	 * デフォルトコンストラクタ
	 * @param baseMap 元になるマップ
	 */
	public LowerMap(final Map<String, T> baseMap){
		super(baseMap);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	public LowerMap<T> clone(){
		LowerMap<T> clone=new LowerMap<T>();
		Map<String, T> inner=cloneMap(this.inner);
		clone.inner=inner;
		return clone;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.ConvertMap#convertKey(java.lang.String)
	 */
	@Override
	protected String convertKey(final String key) {
		if (key==null){
			return null;
		}
		return key.toLowerCase();
	}

	@Override
	public LowerMap<T> newInstance() {
		LowerMap<T> clone=new LowerMap<T>();
		Map<String, T> inner=cast(
			com.sqlapp.util.CommonUtils.newInstance(this.inner));
		clone.inner=inner;
		return clone;
	}
}
