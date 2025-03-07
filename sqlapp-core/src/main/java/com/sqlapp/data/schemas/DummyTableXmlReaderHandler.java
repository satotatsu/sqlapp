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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * TableのXML読み込み
 * 
 * @author satoh
 * 
 */
class DummyTableXmlReaderHandler extends AbstractBaseDbObjectXmlReaderHandler<DummyTable> {

	protected DummyTableXmlReaderHandler(){
		super(()->new DummyTable());
	}
	
	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		// Other
		StaxElementHandler handler = new DummyColumnCollectionXmlReaderHandler();
		register(handler.getLocalName(),
				new AbstractSetValue<DummyTable, DummyColumnCollection>() {
					@Override
					public void setValue(DummyTable target, String name,
							DummyColumnCollection setValue)
							throws XMLStreamException {
						target.setColumns(setValue);
					}
				});
		registerChild(handler);
		//
	}

	@Override
	protected DummyTable createNewInstance() {
		return new DummyTable();
	}

	@Override
	protected DbCommonObject<?> toParent(Object parentObject) {
		return (DbCommonObject<?>)parentObject;
	}

}
