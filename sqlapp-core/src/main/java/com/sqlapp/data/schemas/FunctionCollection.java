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
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

/**
 * ファンクションコレクション
 * 
 * @author satoh
 * 
 */
public final class FunctionCollection extends
		AbstractSchemaObjectCollection<Function> implements HasParent<Schema>
, NewElement<Function, FunctionCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4124416182970544003L;

	/**
	 * コンストラクタ
	 */
	protected FunctionCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected FunctionCollection(Schema schema) {
		super(schema);
	}
	
	@Override
	protected Supplier<FunctionCollection> newInstance(){
		return ()->new FunctionCollection();
	}

	@Override
	public FunctionCollection clone(){
		return (FunctionCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractSchemaObjectCollection#equals(java.lang
	 * .Object, com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof FunctionCollection)) {
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
		return (Schema) super.getParent();
	}

	@Override
	public Function newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Function> getElementSupplier() {
		return ()->new Function();
	}
}
