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
public class DimensionLevelColumnCollection extends
		AbstractSchemaObjectCollection<DimensionLevelColumn> implements
		HasParent<DimensionLevel>
, NewElement<DimensionLevelColumn, DimensionLevelColumnCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DimensionLevelColumnCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected DimensionLevelColumnCollection(DimensionLevel dimensionLevel) {
		super(dimensionLevel);
	}

	@Override
	protected Supplier<DimensionLevelColumnCollection> newInstance(){
		return ()->new DimensionLevelColumnCollection();
	}

	@Override
	public DimensionLevelColumnCollection clone(){
		return (DimensionLevelColumnCollection)super.clone();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DimensionLevelColumnCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionLevel getParent() {
		return (DimensionLevel)super.getParent();
	}
	
	public DimensionLevelColumnCollection add(String name) {
		if (name == null) {
			return this;
		}
		DimensionLevelColumn obj = new DimensionLevelColumn(name);
		this.add(obj);
		return this;
	}

	public DimensionLevelColumnCollection add(String name, Consumer<DimensionLevelColumn> cons) {
		DimensionLevelColumn obj = new DimensionLevelColumn(name);
		this.add(obj);
		cons.accept(obj);
		return this;
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
	public DimensionLevelColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DimensionLevelColumn> getElementSupplier() {
		return ()->new DimensionLevelColumn();
	}

}
