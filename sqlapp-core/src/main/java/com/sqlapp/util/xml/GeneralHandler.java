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

package com.sqlapp.util.xml;

import static com.sqlapp.util.CommonUtils.linkedMap;

import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;

/**
 * localNameに一致する要素の汎用読み込みクラス
 * 
 * @author satoh
 * 
 */
public class GeneralHandler extends AbstractStaxElementHandler {

	private String localName = null;

	@Override
	public String getLocalName() {
		return localName;
	}

	public GeneralHandler(String localName) {
		this.localName = localName;
		this.registerChild(new MapHandler());
		this.registerChild(new ListHandler());
		this.registerChild(new SetHandler());
		this.registerChild(new NotEmptyTextHandler());
		this.registerChild(new EmptyTextSkipHandler());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.AbstractStaxElementHandler#doHandle(com.sqlapp.util
	 * .StaxReader)
	 */
	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		Map<String, String> result = null;
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				int size = reader.getAttributeCount();
				result = linkedMap(size, 1.0f);
				for (int i = 0; i < size; i++) {
					String attributeLocalName = reader.getAttributeLocalName(i);
					String attributeValue = reader.getAttributeValue(i);
					result.put(attributeLocalName, attributeValue);
				}
				reader.next();
			}
			if (match(reader)) {
				reader.next();
				break;
			} else {
				callChilds(reader, result);
			}
		}
		callParent(reader, getLocalName(), parentObject, result);
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object parentObject, Object value)
			throws XMLStreamException {
	}
}