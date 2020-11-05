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
 * Privilegeのコレクション
 * 
 */
public final class RoutinePrivilegeCollection extends
		AbstractDbObjectCollection<RoutinePrivilege> implements
		HasParent<Catalog>
, NewElement<RoutinePrivilege, RoutinePrivilegeCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected RoutinePrivilegeCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected RoutinePrivilegeCollection(Catalog catalog) {
		super(catalog);
	}

	@Override
	protected Supplier<RoutinePrivilegeCollection> newInstance(){
		return ()->new RoutinePrivilegeCollection();
	}
	
	@Override
	public RoutinePrivilegeCollection clone(){
		return (RoutinePrivilegeCollection)super.clone();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RoutinePrivilegeCollection)) {
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
	public Catalog getParent() {
		return (Catalog) super.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObjectCollection#find(com.sqlapp.data
	 * .schemas.AbstractDbObject)
	 */
	@Override
	public RoutinePrivilege find(RoutinePrivilege obj) {
		if (obj == null) {
			return null;
		}
		for (RoutinePrivilege val : this) {
			if (val.like(obj)) {
				return val;
			}
		}
		return null;
	}

	@Override
	public RoutinePrivilege newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<RoutinePrivilege> getElementSupplier() {
		return ()->new RoutinePrivilege();
	}
}
