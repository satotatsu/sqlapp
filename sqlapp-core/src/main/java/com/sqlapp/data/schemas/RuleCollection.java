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
 * Ruleコレクション
 * 
 * @author satoh
 * 
 */
public final class RuleCollection extends AbstractSchemaObjectCollection<Rule>
		implements HasParent<Schema>
, NewElement<Rule, RuleCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5713369502766824420L;

	/**
	 * コンストラクタ
	 */
	protected RuleCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected RuleCollection(Schema schema) {
		super(schema);
	}

	@Override
	protected Supplier<RuleCollection> newInstance(){
		return ()->new RuleCollection();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RuleCollection)) {
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
	public RuleCollection clone() {
		return (RuleCollection)super.clone();
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
	public Rule newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Rule> getElementSupplier() {
		return ()->new Rule();
	}
}
