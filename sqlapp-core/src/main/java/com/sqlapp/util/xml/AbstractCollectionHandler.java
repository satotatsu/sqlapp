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
 * コレクションの読み込み抽象クラス
 * @author satoh
 *
 */
public abstract class AbstractCollectionHandler<T> extends AbstractStaxElementHandler{


	/* (non-Javadoc)
	 * @see com.sqlapp.util.xml.AbstractStaxElementHandler#doHandle(com.sqlapp.util.StaxReader)
	 */
	@Override
	protected void doHandle(StaxReader reader, Object parentObject) throws XMLStreamException {
		Object val=createNewInstance();
		while(reader.hasNext()){
			if (reader.isStartElement()){
				reader.next();
			}
			if (match(reader)&&reader.isEndElement()){
				reader.next();
				break;
			} else{
				callChilds(reader, val);
			}
		}
		callParent(reader, getLocalName(), parentObject, val);
	}
	
	protected abstract T createNewInstance();
}
