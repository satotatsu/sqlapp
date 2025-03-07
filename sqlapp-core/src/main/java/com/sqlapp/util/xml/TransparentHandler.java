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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;

/**
 * 開始、終了をもつ要素のハンドラー
 * 
 * @author tatsuo satoh
 * 
 */
public class TransparentHandler extends AbstractStaxElementHandler {

	public TransparentHandler(StaxElementHandler... staxElementHandlers) {
		super.setChilds(staxElementHandlers);
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				reader.next();
			}
			if (match(reader) && reader.isEndElement()) {
				reader.next();
				break;
			} else {
				callChilds(reader, parentObject);
			}
		}
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childValue)
			throws XMLStreamException {
		super.callParent(reader, this.getLocalName(), ownObject, childValue);
	}

	@Override
	public String getLocalName() {
		return null;
	}
}
