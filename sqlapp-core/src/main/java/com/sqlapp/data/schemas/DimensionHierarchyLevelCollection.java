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

import com.sqlapp.util.SeparatedStringBuilder;

/**
 * DimensionHierarchyLevelCollection
 * 
 */
public class DimensionHierarchyLevelCollection extends AbstractNamedObjectCollection<DimensionHierarchyLevel> implements
		HasParent<DimensionHierarchy>
, NewElement<DimensionHierarchyLevel, DimensionHierarchyLevelCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyLevelCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyLevelCollection(DimensionHierarchy parent) {
		super(parent);
	}

	@Override
	protected Supplier<DimensionHierarchyLevelCollection> newInstance(){
		return ()->new DimensionHierarchyLevelCollection();
	}
	
	@Override
	protected String getSimpleName() {
		return SchemaObjectProperties.DIMENSION_HIERARCHY_LEVELS.getLabel();
	}
	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DimensionHierarchyLevelCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	public String getSchemaName(){
		if (this.getParent()==null){
			return null;
		}
		if (this.getParent().getParent()==null){
			return null;
		}
		return this.getParent().getParent().getSchemaName();
	}
	
	@Override
	public DimensionHierarchyLevelCollection clone() {
		DimensionHierarchyLevelCollection c = new DimensionHierarchyLevelCollection();
		cloneProperties(c);
		return c;
	}

	@Override
	public DimensionHierarchy getParent() {
		return (DimensionHierarchy)super.getParent();
	}

	@Override
	public String toString() {
		return toString(null, null);
	}

	protected String toString(String start, String end) {
		SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
		builder.setStart(start).setEnd(end);
		builder.add(this.inner);
		return builder.toString();
	}

	@Override
	public DimensionHierarchyLevel newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DimensionHierarchyLevel> getElementSupplier() {
		return ()->new DimensionHierarchyLevel();
	}

}
