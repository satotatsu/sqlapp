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
 * ColumnPrivilegeのコレクション
 * 
 */
public final class ColumnPrivilegeCollection extends
		AbstractDbObjectCollection<ColumnPrivilege> implements
		HasParent<Catalog>
	, NewElement<ColumnPrivilege, ColumnPrivilegeCollection>
	{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected ColumnPrivilegeCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected ColumnPrivilegeCollection(Catalog parent) {
		super(parent);
	}

	@Override
	protected Supplier<ColumnPrivilegeCollection> newInstance(){
		return ()->new ColumnPrivilegeCollection();
	}

	@Override
	public ColumnPrivilegeCollection clone(){
		return (ColumnPrivilegeCollection)super.clone();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ColumnPrivilegeCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public Catalog getParent() {
		return (Catalog) super.getParent();
	}

	@Override
	protected Supplier<ColumnPrivilege> getElementSupplier() {
		return ()->new ColumnPrivilege();
	}

	@Override
	public ColumnPrivilege newElement() {
		return super.newElementInternal();
	}

}
