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
 * DimensionHierarchyJoinKeyCollection
 * 
 */
public class DimensionHierarchyJoinKeyCollection extends AbstractDbObjectCollection<DimensionHierarchyJoinKey> implements
		HasParent<DimensionHierarchy>
, NewElement<DimensionHierarchyJoinKey, DimensionHierarchyJoinKeyCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyJoinKeyCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyJoinKeyCollection(DimensionHierarchy parent) {
		super(parent);
	}
	
	@Override
	protected Supplier<DimensionHierarchyJoinKeyCollection> newInstance(){
		return ()->new DimensionHierarchyJoinKeyCollection();
	}
	
	@Override
	public DimensionHierarchyJoinKeyCollection clone(){
		return (DimensionHierarchyJoinKeyCollection)super.clone();
	}

	@Override
	protected String getSimpleName() {
		return SchemaObjectProperties.DIMENSION_HIERARCHY_JOIN_KEYS.getLabel();
	}

	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DimensionHierarchyJoinKeyCollection)) {
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
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public DimensionHierarchy getParent() {
		return (DimensionHierarchy)super.getParent();
	}

	@Override
	public DimensionHierarchyJoinKey newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DimensionHierarchyJoinKey> getElementSupplier() {
		return ()->new DimensionHierarchyJoinKey();
	}
}
