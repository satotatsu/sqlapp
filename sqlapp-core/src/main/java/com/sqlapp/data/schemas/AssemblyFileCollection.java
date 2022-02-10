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

public final class AssemblyFileCollection extends
		AbstractNamedObjectCollection<AssemblyFile> implements
		HasParent<Assembly>
	, NewElement<AssemblyFile, AssemblyFileCollection>
	{

	/** serialVersionUID */
	private static final long serialVersionUID = -1098985877736989046L;

	/**
	 * コンストラクタ
	 */
	protected AssemblyFileCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param assembly
	 */
	protected AssemblyFileCollection(Assembly assembly) {
		super(assembly);
	}
	
	@Override
	protected Supplier<AssemblyFileCollection> newInstance(){
		return ()->new AssemblyFileCollection();
	}
	
	@Override
	public AssemblyFileCollection clone(){
		return (AssemblyFileCollection)super.clone();
	}

	public Assembly getAssembly() {
		return getParent();
	}

	@Override
	public Assembly getParent(){
		return (Assembly)super.getParent();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AssemblyFileCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	@Override
	protected Supplier<AssemblyFile> getElementSupplier() {
		return ()->new AssemblyFile();
	}
	
	@Override
	public AssemblyFile newElement(){
		return super.newElementInternal();
	}
}
