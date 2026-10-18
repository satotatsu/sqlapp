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
import java.util.Collection;
import java.util.ArrayList;

public final class ReferenceTableSpaceCollection extends
	AbstractDbObjectCollection<TableSpace> implements HasParent<PartitionScheme>
, NewElement<TableSpace, ReferenceTableSpaceCollection>{

	/** serialVersionUID */
	private static final long serialVersionUID = -4339668632731453446L;

	/**
	 * コンストラクタ
	 */
	protected ReferenceTableSpaceCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected ReferenceTableSpaceCollection(PartitionScheme partitionScheme) {
		super(partitionScheme);
	}

	@Override
	protected Supplier<ReferenceTableSpaceCollection> newInstance(){
		return ()->new ReferenceTableSpaceCollection();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ReferenceTableSpaceCollection)) {
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
	public PartitionScheme getParent() {
		return (PartitionScheme) super.getParent();
	}

	@Override
	protected AbstractBaseDbObjectCollectionXmlReaderHandler<ReferenceTableSpaceCollection> getDbObjectXmlReaderHandler() {
		return new AbstractBaseDbObjectCollectionXmlReaderHandler<ReferenceTableSpaceCollection>(this.newInstance()) {

			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				setChild(newElementInternal().getDbObjectXmlReaderHandler().setSetParent(false));
			}

			@Override
			public String getLocalName() {
				return SchemaObjectProperties.REFERENCE_TABLE_SPACES.getLabel();
			}
		};
	}
	
	/** Partition destinations are ordered references and may repeat the same filegroup. */
	@Override
	public boolean addAll(Collection<? extends TableSpace> values) {
		boolean changed = false;
		for (TableSpace value : new ArrayList<>(values)) changed |= add(value);
		return changed;
	}

	@Override
	public boolean addAll(int index, Collection<? extends TableSpace> values) {
		if (index < 0 || index > size()) throw new IndexOutOfBoundsException(index);
		var copy = new ArrayList<>(values);
		for (TableSpace value : copy) add(index++, value);
		return !copy.isEmpty();
	}

	@Override
	protected void setElementParent(TableSpace e) {
	}
	
	public ReferenceTableSpaceCollection add(String name){
		this.add(new TableSpace(name));
		return this;
	}

	@Override
	public TableSpace newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<TableSpace> getElementSupplier() {
		return ()->new TableSpace();
	}

}
