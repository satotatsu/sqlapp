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
 * ユーザーに対応したオブジェクトコレクション
 * 
 * @author satoh
 * 
 */
public final class UserCollection extends AbstractNamedObjectCollection<User>
		implements HasParent<Catalog>
, NewElement<User, UserCollection>{

	/** serialVersionUID */
	private static final long serialVersionUID = 1;

	/**
	 * コンストラクタ
	 */
	protected UserCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected UserCollection(Catalog catalog) {
		super(catalog);
	}

	@Override
	protected Supplier<UserCollection> newInstance(){
		return ()->new UserCollection();
	}

	@Override
	public UserCollection clone(){
		return (UserCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof UserCollection)) {
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

	@Override
	public User newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<User> getElementSupplier() {
		return ()->new User();
	}
}
