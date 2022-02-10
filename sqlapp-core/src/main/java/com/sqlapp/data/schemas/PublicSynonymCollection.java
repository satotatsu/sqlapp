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
 * シノニムコレクション
 * 
 * @author satoh
 * 
 */
public final class PublicSynonymCollection extends
		AbstractNamedObjectCollection<PublicSynonym> implements
		HasParent<Catalog>
, NewElement<PublicSynonym, PublicSynonymCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -486906552731963408L;

	/**
	 * コンストラクタ
	 */
	protected PublicSynonymCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected PublicSynonymCollection(Catalog catalog) {
		super(catalog);
	}
	
	@Override
	protected Supplier<PublicSynonymCollection> newInstance(){
		return ()->new PublicSynonymCollection();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof PublicSynonymCollection)) {
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
	public PublicSynonymCollection clone() {
		return (PublicSynonymCollection)super.clone();
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
	public PublicSynonym newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<PublicSynonym> getElementSupplier() {
		return ()->new PublicSynonym();
	}
}
