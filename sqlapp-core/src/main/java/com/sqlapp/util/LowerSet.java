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

import static com.sqlapp.util.CommonUtils.cast;

import java.util.Set;

/**
 * 小文字のセット
 * @author satoh
 *
 */
public class LowerSet extends AbstractStringSet {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6402418167751145791L;
	/**
	 * コンストラクタ
	 */
	public LowerSet(){
	}

	/**
	 * コンストラクタ
	 * @param capacity マップの初期サイズ
	 */
	public LowerSet(final int capacity){
		super(capacity);
	}

	/**
	 * コンストラクタ
	 * @param capacity マップの初期サイズ
	 */
	public LowerSet(final int capacity, final float loadFactor){
		super(capacity, loadFactor);
	}
	
	/**
	 * デフォルトコンストラクタ
	 * @param baseSet 元になるSet
	 */
	public LowerSet(final Set<String> baseSet){
		super(baseSet);
	}
	
	@Override
	protected String convertKey(String key) {
		if (key==null){
			return null;
		}
		return key.toLowerCase();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	public LowerSet clone(){
		try {
			return cast(super.clone());
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractStringSet#newInstance()
	 */
	public LowerSet newInstance(){
		LowerSet clone=new LowerSet();
		Set<String> inner=cast(
			com.sqlapp.util.CommonUtils.newInstance(this.inner));
		clone.inner=inner;
		return clone;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString(){
		return com.sqlapp.util.CommonUtils.toString(this);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.util.AbstractStringSet#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof LowerSet)){
			return false;
		}
		return true;
	}
}
