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
public final class ProcedureCollection extends
		AbstractSchemaObjectCollection<Procedure> implements HasParent<Schema>
, NewElement<Procedure, ProcedureCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4124416182970544003L;

	/**
	 * コンストラクタ
	 */
	protected ProcedureCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected ProcedureCollection(Schema schema) {
		super(schema);
	}
	
	@Override
	protected Supplier<ProcedureCollection> newInstance(){
		return ()->new ProcedureCollection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ProcedureCollection)) {
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
	 * @see com.sqlapp.dataset.AbstractSchemaObjectList#clone()
	 */
	@Override
	public ProcedureCollection clone() {
		return (ProcedureCollection)super.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Schema getParent() {
		return this.getSchema();
	}

	@Override
	public Procedure newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Procedure> getElementSupplier() {
		return ()->new Procedure();
	}
}
