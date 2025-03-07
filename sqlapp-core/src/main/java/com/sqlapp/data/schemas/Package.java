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
public final class Package extends AbstractSchemaObject<Package> implements
		HasParent<PackageCollection>, Body<PackageBody> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -233129040207248096L;

	/**
	 * コンストラクタ
	 */
	public Package() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param packageName
	 */
	public Package(String packageName) {
		super(packageName);
	}
	
	@Override
	protected Supplier<Package> newInstance(){
		return ()->new Package();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Package)) {
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
	public PackageCollection getParent() {
		return (PackageCollection) super.getParent();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
	}

	@Override
	public PackageBody getBody(){
		if (this.getParent()!=null&&this.getParent().getParent()!=null){
			return this.getParent().getParent().getPackageBodies().get(this.getName());
		}
		return null;
	}
	
}
