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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;

/**
 * ReferenceColumnCollectionのXML読み込み
 * 
 * @author satoh
 * 
 */
class ReferenceColumnCollectionXmlReaderHandler extends
		AbstractNamedObjectCollectionXmlReaderHandler<ReferenceColumnCollection> {

	protected ReferenceColumnCollectionXmlReaderHandler() {
		super(()->new ReferenceColumnCollection());
	}

	public ReferenceColumnCollectionXmlReaderHandler(String localName) {
		super(()->new ReferenceColumnCollection());
		this.localName = localName;
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		setChild(new ReferenceColumnXmlReaderHandler());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractNamedObjectListHandler#createNewInstance()
	 */
	@Override
	protected ReferenceColumnCollection createNewInstance() {
		return new ReferenceColumnCollection();
	}

	private String localName = SchemaObjectProperties.COLUMNS.getLabel();

	@Override
	public String getLocalName() {
		return localName;
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		ReferenceColumnCollection obj = null;
		if (parentObject instanceof ChildObjectHolder) {
			obj = ((ChildObjectHolder) parentObject).getValue();
		} else {
			obj = createNewInstance(parentObject);
		}
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				Map<String, String> map = getAttributeMap(reader);
				String name = map.get("name");
				String schemaName = map.get("schemaName");
				if (!isEmpty(name)) {
					obj = getInstance(parentObject, name, schemaName, obj);
				}
				for (Map.Entry<String, String> entry : map.entrySet()) {
					setValue(obj, entry.getKey(), entry.getValue());
				}
				reader.next();
			}
			if (match(reader)) {
				reader.next();
				break;
			} else {
				callChilds(reader, obj);
			}
		}
		finishDoHandle(reader, parentObject, obj);
		callParent(reader, getLocalName(), parentObject, obj);
	}

	protected ReferenceColumnCollection getInstance(Object parentObject,
			String name, String schemaName, ReferenceColumnCollection obj) {
		if (parentObject instanceof Index) {
			Index index = (Index) parentObject;
			return index.getColumns();
		}else if (parentObject instanceof ForeignKeyConstraint) {
			ForeignKeyConstraint fk = (ForeignKeyConstraint) parentObject;
			return fk.getRelatedColumns();
		} else if (parentObject instanceof MviewLog) {
			MviewLog mviewLog = (MviewLog) parentObject;
			return mviewLog.getColumns();
		}
		return obj;
	}

	@Override
	protected ReferenceColumnCollection getInstance(Object parentObject,
			ReferenceColumnCollection obj) {
		return getInstance(parentObject, null, null, obj);
	}
}
