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

import com.sqlapp.util.xml.EmptyTextSkipHandler;

/**
 * DataColumnCollectionのXML読み込み
 * 
 * @author satoh
 * 
 */
class ColumnCollectionXmlReaderHandler extends AbstractNamedObjectCollectionXmlReaderHandler<ColumnCollection> {

	public ColumnCollectionXmlReaderHandler() {
		super(()->new ColumnCollection());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		setChild(new Column().getDbObjectXmlReaderHandler());
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
	protected ColumnCollection getInstance(Object parentObject,
			ColumnCollection obj) {
		if (parentObject instanceof Table) {
			Table table = (Table) parentObject;
			if (table.getColumns() != null) {
				return table.getColumns();
			}
		}
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectListHandler#createNewInstance()
	 */
	@Override
	protected ColumnCollection createNewInstance() {
		return new ColumnCollection();
	}
}
