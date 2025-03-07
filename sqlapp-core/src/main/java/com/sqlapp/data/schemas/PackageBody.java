/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import com.sqlapp.util.ToStringBuilder;

/**
 * パッケージ(Oracle)
 * 
 * @author satoh
 * 
 */
public final class PackageBody extends AbstractSchemaObject<PackageBody>
		implements HasParent<PackageBodyCollection> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -233129040207248096L;

	/**
	 * コンストラクタ
	 */
	public PackageBody() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public PackageBody(String name) {
		super(name);
	}

	@Override
	protected Supplier<PackageBody> newInstance(){
		return ()->new PackageBody();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedTextObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof PackageBody)) {
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
	public PackageBodyCollection getParent() {
		return (PackageBodyCollection) super.getParent();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
	}

}
