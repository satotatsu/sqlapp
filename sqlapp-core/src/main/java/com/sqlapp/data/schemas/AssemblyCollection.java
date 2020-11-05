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
 * CLRアセンブリコレクション
 * 
 * @author satoh
 * 
 */
public class AssemblyCollection extends AbstractNamedObjectCollection<Assembly>
		implements HasParent<Catalog>
	, NewElement<Assembly, AssemblyCollection>
	{

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected AssemblyCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected AssemblyCollection(Catalog parent) {
		super(parent);
	}

	@Override
	protected Supplier<AssemblyCollection> newInstance(){
		return ()->new AssemblyCollection();
	}
	
	@Override
	public AssemblyCollection clone(){
		return (AssemblyCollection)super.clone();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AssemblyCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public Catalog getParent() {
		return (Catalog) super.getParent();
	}

	@Override
	protected Supplier<Assembly> getElementSupplier() {
		return ()->new Assembly();
	}
	
	@Override
	public Assembly newElement(){
		return super.newElementInternal();
	}
}
