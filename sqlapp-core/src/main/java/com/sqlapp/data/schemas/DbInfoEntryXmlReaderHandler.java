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

import static com.sqlapp.util.CommonUtils.cast;

import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.AbstractCollectionHandler;
import com.sqlapp.util.xml.EmptyTextSkipHandler;
import com.sqlapp.util.xml.EntryHandler;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * DBInfoの読み込みクラス
 * 
 * @author satoh
 * 
 */
class DbInfoEntryXmlReaderHandler extends AbstractCollectionHandler<DbInfoEntry> {

	@Override
	public String getLocalName() {
		return "product";
	}

	protected DbInfoEntryXmlReaderHandler() {
		this.registerChild(new EntryHandler());
		this.registerChild(new EmptyTextSkipHandler());
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		DbInfoEntry val = createNewInstance();
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				Map<String, String> map = getAttributeMap(reader);
				val.setProductName(map.get(SchemaProperties.NAME.getLabel()));
				reader.next();
			}
			if (match(reader) && reader.isEndElement()) {
				reader.next();
				break;
			} else {
				callChilds(reader, val);
			}
		}
		callParent(reader, getLocalName(), parentObject, val);
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		DbInfoEntry dbInfoEntry = cast(ownObject);
		if (childObject != null) {
			Entry<String, String> entry = cast(childObject);
			dbInfoEntry.getKeyValues().put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	protected DbInfoEntry createNewInstance() {
		return new DbInfoEntry();
	}
}
