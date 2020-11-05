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

/**
 * ColumnのXML読み込み
 * 
 * @author satoh
 * 
 */
class DummyColumnXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<DummyColumn> {

	public DummyColumnXmlReaderHandler() {
		super(()->new DummyColumn());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.xml.AbstractSchemaHandler#createNewInstance()
	 */
	@Override
	protected DummyColumn createNewInstance() {
		return new DummyColumn();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectHandler#getParent(java.lang.Object)
	 */
	@Override
	protected DummyColumnCollection toParent(Object parentObject) {
		DummyColumnCollection parent = null;
		if (parentObject instanceof DummyTable) {
			parent = ((DummyTable) parentObject).getColumns();
		} else if (parentObject instanceof DummyColumnCollection) {
			parent = (DummyColumnCollection) parentObject;
		}
		return parent;
	}
}
