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
 * シーケンスコレクション
 * 
 * @author satoh
 * 
 */
public final class SequenceCollection extends
		AbstractSchemaObjectCollection<Sequence> implements HasParent<Schema>
, NewElement<Sequence, SequenceCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -1317229769901885557L;

	/**
	 * コンストラクタ
	 */
	protected SequenceCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected SequenceCollection(Schema schema) {
		super(schema);
	}
	
	@Override
	protected Supplier<SequenceCollection> newInstance(){
		return ()->new SequenceCollection();
	}

	/**
	 * equals
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof SequenceCollection)) {
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
	public SequenceCollection clone() {
		return (SequenceCollection)super.clone();
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
	public Sequence newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Sequence> getElementSupplier() {
		return ()->new Sequence();
	}
}
