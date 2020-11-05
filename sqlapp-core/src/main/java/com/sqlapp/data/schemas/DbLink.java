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
 * DBリンク
 * 
 * @author satoh
 * 
 */
public final class DbLink extends AbstractObjectLink<DbLink> implements
		HasParent<DbLinkCollection> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5594263228976491676L;

	/**
	 * コンストラクタ
	 */
	protected DbLink() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public DbLink(String name) {
		super(name);
	}

	@Override
	protected Supplier<DbLink> newInstance(){
		return ()->new DbLink();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractObjectLink#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DbLink)) {
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
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public DbLinkCollection getParent() {
		return (DbLinkCollection) super.getParent();
	}

}
