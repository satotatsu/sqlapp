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
package com.sqlapp.util.xml;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;

/**
 * null読み込みクラス
 * 
 * @author satoh
 * 
 */
public class NullHandler extends AbstractStaxElementHandler {

	@Override
	public String getLocalName() {
		return StaxWriter.NULL_ELEMENT;
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		String value = null;
		if (reader.isStartElement()) {
			reader.next();
			if (reader.isEndElement()) {
				reader.next();
			} else {
				reader.raiseElementException(getLocalName());
			}
		}
		callParent(reader, getLocalName(), parentObject, value);
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object parentObject, Object value)
			throws XMLStreamException {
	}
}
