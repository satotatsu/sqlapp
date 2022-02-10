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
 * RoleMemberのコレクション
 * 
 */
public final class RoleMemberCollection extends
		AbstractDbObjectCollection<RoleMember> implements HasParent<Catalog>
, NewElement<RoleMember, RoleMemberCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected RoleMemberCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected RoleMemberCollection(Catalog catalog) {
		super(catalog);
	}

	@Override
	protected Supplier<RoleMemberCollection> newInstance(){
		return ()->new RoleMemberCollection();
	}
	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RoleMemberCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public RoleMemberCollection clone() {
		return (RoleMemberCollection)super.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Catalog getParent() {
		return (Catalog) super.getParent();
	}

	@Override
	public RoleMember newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<RoleMember> getElementSupplier() {
		return ()->new RoleMember();
	}
}
