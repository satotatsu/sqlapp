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
