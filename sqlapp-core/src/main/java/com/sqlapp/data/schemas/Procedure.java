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

/**
 * プロシージャ
 * 
 * @author satoh
 * 
 */
public final class Procedure extends ArgumentRoutine<Procedure> implements
		HasParent<ProcedureCollection> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -235245367287317519L;

	/**
	 * コンストラクタ
	 */
	public Procedure() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param procedureName
	 */
	public Procedure(String procedureName) {
		super(procedureName);
	}

	@Override
	protected Supplier<Procedure> newInstance(){
		return ()->new Procedure();
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param procedureName
	 * @param specificName
	 */
	public Procedure(String procedureName, String specificName) {
		super(procedureName, specificName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedTextObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Procedure)) {
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
	public ProcedureCollection getParent() {
		return (ProcedureCollection) super.getParent();
	}
}
