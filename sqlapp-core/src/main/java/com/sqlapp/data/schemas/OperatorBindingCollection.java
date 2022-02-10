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
 * OperatorBindingのコレクション
 * 
 */
public class OperatorBindingCollection extends
		AbstractDbObjectCollection<OperatorBinding> implements UnOrdered,
		HasParent<Operator>
, NewElement<OperatorBinding, OperatorBindingCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected OperatorBindingCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected OperatorBindingCollection(Operator parent) {
		super(parent);
	}

	@Override
	protected Supplier<OperatorBindingCollection> newInstance(){
		return ()->new OperatorBindingCollection();
	}
	
	@Override
	public OperatorBindingCollection clone(){
		return (OperatorBindingCollection)super.clone();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof OperatorBindingCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * @return the operator
	 */
	@Override
	public Operator getParent() {
		return (Operator)super.getParent();
	}

	@Override
	protected void afterAdd(OperatorBinding argument) {
		if (getParent() != null) {
			getParent().renewParent();
			this.renew();
		}
	}

	@Override
	protected void afterRemove(OperatorBinding argument) {
		if (getParent() != null) {
			getParent().renewParent();
			this.renew();
		}
	}

	@Override
	protected String getSimpleName() {
		return "bindings";
	}

	@Override
	public OperatorBinding newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<OperatorBinding> getElementSupplier() {
		return ()->new OperatorBinding();
	}

}
