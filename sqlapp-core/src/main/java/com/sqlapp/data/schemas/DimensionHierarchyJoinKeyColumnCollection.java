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

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.sqlapp.util.SeparatedStringBuilder;

/**
 * DimensionLevelColumnのコレクション
 * 
 */
public class DimensionHierarchyJoinKeyColumnCollection extends AbstractDbObjectCollection<DimensionHierarchyJoinKeyColumn> implements
		HasParent<DimensionHierarchyJoinKey>
	, NewElement<DimensionHierarchyJoinKeyColumn, DimensionHierarchyJoinKeyColumnCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyJoinKeyColumnCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected DimensionHierarchyJoinKeyColumnCollection(DimensionHierarchyJoinKey parent) {
		super(parent);
	}
	
	@Override
	protected Supplier<DimensionHierarchyJoinKeyColumnCollection> newInstance(){
		return ()->new DimensionHierarchyJoinKeyColumnCollection();
	}

	@Override
	public DimensionHierarchyJoinKeyColumnCollection clone(){
		return (DimensionHierarchyJoinKeyColumnCollection)super.clone();
	}

	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DimensionHierarchyJoinKeyColumnCollection)) {
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
		if (this.getParent().getParent().getParent()==null){
			return null;
		}
		return this.getParent().getParent().getParent().getSchemaName();
	}
	
	@Override
	protected String getSimpleName() {
		return "columns";
	}

	public DimensionHierarchyJoinKeyColumnCollection add(String name) {
		DimensionHierarchyJoinKeyColumn obj = new DimensionHierarchyJoinKeyColumn(name);
		this.add(obj);
		return this;
	}

	public DimensionHierarchyJoinKeyColumnCollection add(String name, Consumer<DimensionHierarchyJoinKeyColumn> cons) {
		DimensionHierarchyJoinKeyColumn obj = new DimensionHierarchyJoinKeyColumn(name);
		this.add(obj);
		cons.accept(obj);
		return this;
	}

	@Override
	public DimensionHierarchyJoinKey getParent() {
		return (DimensionHierarchyJoinKey)super.getParent();
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
	public DimensionHierarchyJoinKeyColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DimensionHierarchyJoinKeyColumn> getElementSupplier() {
		return ()->new DimensionHierarchyJoinKeyColumn();
	}

}
