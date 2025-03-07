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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TypeColumnのコレクション
 * 
 */
public final class TypeColumnCollection extends
		AbstractSchemaObjectCollection<TypeColumn> implements HasParent<Type>
, NewElement<TypeColumn, TypeColumnCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4500961268819976110L;

	/**
	 * コンストラクタ
	 */
	protected TypeColumnCollection() {
	}

	@Override
	protected Supplier<TypeColumnCollection> newInstance(){
		return ()->new TypeColumnCollection();
	}
	
	@Override
	public TypeColumnCollection clone(){
		return (TypeColumnCollection)super.clone();
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param type
	 */
	protected TypeColumnCollection(Type type) {
		super(type);
	}

	public TypeColumnCollection add(String name) {
		if (name == null) {
			return this;
		}
		TypeColumn obj = new TypeColumn(name);
		this.add(obj);
		return this;
	}

	public TypeColumnCollection add(String name, Consumer<TypeColumn> cons) {
		TypeColumn obj = new TypeColumn(name);
		this.add(obj);
		cons.accept(obj);
		return this;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObjectCollection#beforeAdd(com.sqlapp
	 * .data.schemas.AbstractDbObject)
	 */
	@Override
	protected boolean beforeAdd(TypeColumn column) {
		if (this.getParent() != null) {
			column.setTypeName(null);
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TypeColumnCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected String getSimpleName(){
		return "columns";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Type getParent() {
		return (Type)super.getParent();
	}

	@Override
	public void sort() {
	}

	public Schema getSchema() {
		if (this.getParent() == null) {
			return super.getSchema();
		}
		if (this.getParent().getParent() == null) {
			return super.getSchema();
		}
		return this.getParent().getParent().getSchema();
	}

	@Override
	public TypeColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<TypeColumn> getElementSupplier() {
		return ()->new TypeColumn();
	}
}
