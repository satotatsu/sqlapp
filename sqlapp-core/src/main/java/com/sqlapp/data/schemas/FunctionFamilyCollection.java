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
 * FunctionFamilyのコレクション
 * 
 */
public class FunctionFamilyCollection extends
		AbstractDbObjectCollection<FunctionFamily> implements UnOrdered,
		HasParent<OperatorClass>
	, NewElement<FunctionFamily, FunctionFamilyCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected FunctionFamilyCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected FunctionFamilyCollection(OperatorClass parent) {
		super(parent);
	}
	
	@Override
	protected Supplier<FunctionFamilyCollection> newInstance(){
		return ()->new FunctionFamilyCollection();
	}

	@Override
	public FunctionFamilyCollection clone(){
		return (FunctionFamilyCollection)super.clone();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof FunctionFamilyCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * @return the operatorClass
	 */
	@Override
	public OperatorClass getParent() {
		return (OperatorClass)super.getParent();
	}

	@Override
	public FunctionFamily newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<FunctionFamily> getElementSupplier() {
		return ()->new FunctionFamily();
	}
}
