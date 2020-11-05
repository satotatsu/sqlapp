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
 * Domainのコレクション
 * 
 */
public final class DomainCollection extends
		AbstractSchemaObjectCollection<Domain> implements HasParent<Schema>
, NewElement<Domain, DomainCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4500961268819976110L;

	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected DomainCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param schema
	 */
	protected DomainCollection(Schema schema) {
		super(schema);
	}

	@Override
	protected Supplier<DomainCollection> newInstance(){
		return ()->new DomainCollection();
	}
	
	@Override
	public DomainCollection clone(){
		return (DomainCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DomainCollection)) {
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
	public Domain newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Domain> getElementSupplier() {
		return ()->new Domain();
	}
}
