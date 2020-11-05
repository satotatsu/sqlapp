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
 * OperatorClassコレクション
 * 
 * @author satoh
 * 
 */
public final class OperatorClassCollection extends
		AbstractSchemaObjectCollection<OperatorClass> implements
		HasParent<Schema> 
	, NewElement<OperatorClass, OperatorClassCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3484209833472149870L;

	/**
	 * コンストラクタ
	 */
	protected OperatorClassCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected OperatorClassCollection(Schema schema) {
		super(schema);
	}
	
	@Override
	protected Supplier<OperatorClassCollection> newInstance(){
		return ()->new OperatorClassCollection();
	}

	@Override
	public OperatorClassCollection clone(){
		return (OperatorClassCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractSchemaObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof OperatorClassCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected String getSimpleName() {
		return "operatorClasses";
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public Schema getParent() {
		return (Schema) super.getSchema();
	}

	@Override
	public OperatorClass newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<OperatorClass> getElementSupplier() {
		return ()->new OperatorClass();
	}
}
