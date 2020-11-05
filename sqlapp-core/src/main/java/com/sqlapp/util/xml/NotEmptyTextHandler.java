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
/**
 * 文字読み込みクラス
 * @author satoh
 *
 */
public class NotEmptyTextHandler extends EmptyTextSkipHandler{

	@Override
	public String getLocalName() {
		return null;
	}

	public boolean match(StaxReader reader){
		return !super.match(reader);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.util.xml.AbstractStaxElementHandler#doHandle(com.sqlapp.util.StaxReader)
	 */
	@Override
	protected void doHandle(StaxReader reader, Object parentObject) throws XMLStreamException {
		String localName=null;
		if (getLocalName()==null){
			if(reader.isStartElement()){
				localName=reader.getLocalName();
				reader.next();
			}
		} else{
			localName=getLocalName();
		}
		if(reader.isEndElement()){
			localName=reader.getLocalName();
			callParent(reader, localName, parentObject, null);
			reader.next();
		} else{
			if(reader.hasNext()){
				callParent(reader, localName, parentObject, reader.getText());
				reader.next();
			}
		}
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child, String name, Object ownObject, Object childObject) throws XMLStreamException {
	}
}
