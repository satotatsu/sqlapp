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

import com.sqlapp.data.db.dialect.Dialect;

/**
 * OperatorFamilyのコレクション
 * 
 */
public class OperatorFamilyCollection extends
		AbstractDbObjectCollection<OperatorFamily> implements UnOrdered,
		HasParent<OperatorClass>
	, NewElement<OperatorFamily, OperatorFamilyCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected OperatorFamilyCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected OperatorFamilyCollection(OperatorClass operatorClass) {
		super(operatorClass);
	}

	@Override
	protected Supplier<OperatorFamilyCollection> newInstance(){
		return ()->new OperatorFamilyCollection();
	}
	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof OperatorFamilyCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public OperatorFamilyCollection clone() {
		return (OperatorFamilyCollection)super.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public OperatorClass getParent() {
		return (OperatorClass)super.getParent();
	}

	public Dialect getDialect() {
		if (this.getParent() != null) {
			return getParent().getDialect();
		}
		return null;
	}

	/**
	 * スキーマを取得します
	 * 
	 */
	public Schema getSchema() {
		return this.getAncestor(Schema.class);
	}

	/**
	 * スキーマ名を取得します
	 * 
	 */
	public String getSchemaName() {
		if (this.getParent() != null) {
			return this.getParent().getSchemaName();
		}
		return null;
	}

	@Override
	public OperatorFamily newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<OperatorFamily> getElementSupplier() {
		return ()->new OperatorFamily();
	}
}
