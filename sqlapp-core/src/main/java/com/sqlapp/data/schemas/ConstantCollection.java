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

package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/**
 * 定数コレクションクラス
 * 
 * @author satoh
 * 
 */
public final class ConstantCollection extends
		AbstractSchemaObjectCollection<Constant> implements Cloneable,
		HasParent<Schema>
	, NewElement<Constant, ConstantCollection>
{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected ConstantCollection() {
	}

	/**
	 * 
	 * @param parent
	 */
	protected ConstantCollection(Schema parent) {
		super(parent);
	}

	@Override
	protected Supplier<ConstantCollection> newInstance(){
		return ()->new ConstantCollection();
	}
	
	@Override
	public ConstantCollection clone(){
		return (ConstantCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ConstantCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Schema getParent() {
		return this.getSchema();
	}
	
	@Override
	protected Supplier<Constant> getElementSupplier() {
		return ()->new Constant();
	}
	
	@Override
	public Constant newElement(){
		return super.newElementInternal();
	}
}
