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
 * Catalog Collection
 * 
 * @author tatsuo satoh
 * 
 */
public class CatalogCollection extends AbstractBaseDbObjectCollection<Catalog>
		implements RowIteratorHandlerProperty
	, NewElement<Catalog, CatalogCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7610072723986900125L;

	@Override
	protected Supplier<CatalogCollection> newInstance(){
		return ()->new CatalogCollection();
	}

	@Override
	public CatalogCollection clone(){
		return (CatalogCollection)super.clone();
	}
	
	@Override
	protected AbstractBaseDbObjectCollectionXmlReaderHandler<CatalogCollection> getDbObjectXmlReaderHandler() {
		return new AbstractBaseDbObjectCollectionXmlReaderHandler<CatalogCollection>(newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				setChild(newElement().getDbObjectXmlReaderHandler());
			}
			
			@Override
			protected CatalogCollection getInstance(Object parentObject,
					CatalogCollection obj) {
				if (parentObject instanceof CatalogCollection) {
					return ((CatalogCollection) parentObject);
				}
				return obj;
			}
		};
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandlerProperty#setRowIteratorHandler
	 * (com.sqlapp.data.schemas.RowIteratorHandler)
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		for (Catalog catalog : this) {
			catalog.setRowIteratorHandler(rowIteratorHandler);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof CatalogCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	@Override
	public Catalog newElement(){
		Catalog catalog=new Catalog();
		catalog.setParent(this);
		return catalog;
	}
	
}
