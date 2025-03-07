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

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.sqlapp.util.SeparatedStringBuilder;

/**
 * DimensionAttributeColumnCollection
 * 
 */
public class DimensionAttributeColumnCollection extends
		AbstractSchemaObjectCollection<DimensionAttributeColumn> implements
		HasParent<DimensionAttribute>
	, NewElement<DimensionAttributeColumn, DimensionAttributeColumnCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DimensionAttributeColumnCollection() {
	}

	@Override
	protected Supplier<DimensionAttributeColumnCollection> newInstance(){
		return ()->new DimensionAttributeColumnCollection();
	}
	
	@Override
	public DimensionAttributeColumnCollection clone(){
		return (DimensionAttributeColumnCollection)super.clone();
	}
	
	@Override
	protected String getSimpleName() {
		return SchemaObjectProperties.DIMENSION_ATTRIBUTE_COLUMNS.getLabel();
	}

	/**
	 * コンストラクタ
	 */
	protected DimensionAttributeColumnCollection(DimensionAttribute parent) {
		super(parent);
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DimensionAttributeColumnCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionAttribute getParent() {
		return (DimensionAttribute)super.getParent();
	}

	/**
	 * DimensionLevelColumnを追加します
	 * 
	 * @param name
	 */
	public void add(String name) {
		if (name == null) {
			return;
		}
		this.add(new DimensionAttributeColumn(name));
	}

	public DimensionAttributeColumnCollection add(String name, Consumer<DimensionAttributeColumn> cons) {
		DimensionAttributeColumn obj = new DimensionAttributeColumn(name);
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
	public DimensionAttributeColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DimensionAttributeColumn> getElementSupplier() {
		return ()->new DimensionAttributeColumn();
	}

}
