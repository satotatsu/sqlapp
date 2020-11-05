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
 * OperatorBindingArgument Collection
 * 
 */
public class OperatorBindingArgumentCollection extends
		AbstractDbObjectCollection<OperatorBindingArgument> implements UnOrdered,
		HasParent<OperatorBinding>
, NewElement<OperatorBindingArgument, OperatorBindingArgumentCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected OperatorBindingArgumentCollection() {
	}

	/**
	 * コンストラクタ
	 */
	public OperatorBindingArgumentCollection(OperatorBinding parent) {
		super(parent);
	}
	
	@Override
	protected Supplier<OperatorBindingArgumentCollection> newInstance(){
		return ()->new OperatorBindingArgumentCollection();
	}
	
	@Override
	public OperatorBindingArgumentCollection clone(){
		return (OperatorBindingArgumentCollection)super.clone();
	}


	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof OperatorBindingArgumentCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected String getSimpleName() {
		return "arguments";
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public OperatorBinding getParent() {
		return (OperatorBinding)super.getParent();
	}

	@Override
	public OperatorBindingArgument newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<OperatorBindingArgument> getElementSupplier() {
		return ()->new OperatorBindingArgument();
	}

}
