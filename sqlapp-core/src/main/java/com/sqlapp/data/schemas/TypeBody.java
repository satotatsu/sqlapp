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
 * ユーザー定義型(STRUCT)
 * 
 * @author satoh
 * 
 */
public final class TypeBody extends AbstractSchemaObject<TypeBody> implements
		HasParent<TypeBodyCollection> {

	/** serialVersionUID */
	private static final long serialVersionUID = -3342889628196820619L;

	/**
	 * コンストラクタ
	 */
	protected TypeBody() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public TypeBody(String name) {
		super(name);
	}

	@Override
	protected Supplier<TypeBody> newInstance(){
		return ()->new TypeBody();
	}
	
	@Override
	protected void toString(ToStringBuilder builder) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TypeBody)) {
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
	public TypeBodyCollection getParent() {
		return (TypeBodyCollection) super.getParent();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
	}
}
