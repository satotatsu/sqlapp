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

import com.sqlapp.util.xml.EmptyTextSkipHandler;

/**
 * Dummy Column Collection
 * 
 * @author satoh
 * 
 */
class DummyColumnCollectionXmlReaderHandler extends
	AbstractNamedObjectCollectionXmlReaderHandler<DummyColumnCollection> {

	protected DummyColumnCollectionXmlReaderHandler() {
		super(()->new DummyColumnCollection());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		setChild(new DummyColumnXmlReaderHandler());
		this.registerChild(new EmptyTextSkipHandler());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.xml.AbstractSchemaHandler#getInstance(java.lang.Object
	 * , java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	protected DummyColumnCollection getInstance(Object parentObject,
			DummyColumnCollection obj) {
		if (parentObject instanceof DummyTable) {
			DummyTable table = (DummyTable) parentObject;
			if (table.getColumns() != null) {
				return table.getColumns();
			}
		}
		return obj;
	}

}
