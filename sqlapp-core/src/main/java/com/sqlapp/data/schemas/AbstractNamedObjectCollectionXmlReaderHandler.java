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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.xml.AbstractSetValue;

abstract class AbstractNamedObjectCollectionXmlReaderHandler<T extends AbstractNamedObjectCollection<?>>
		extends AbstractBaseDbObjectCollectionXmlReaderHandler<T> {

	protected AbstractNamedObjectCollectionXmlReaderHandler(Supplier<T> supplier) {
		super(supplier);
	}
	
	protected void setChild(AbstractNamedObjectXmlReaderHandler<?> handler) {
		register(handler.getLocalName(), new AbstractSetValue<T, Object>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public void setValue(T target, String name, Object setValue)
					throws XMLStreamException {
				((AbstractNamedObjectCollection) target)
						.add((AbstractNamedObject) setValue);
			}
		});
		registerChild(handler);
	}

	protected void setChild(String localName,
			AbstractNamedObjectXmlReaderHandler<?> handler) {
		register(localName, new AbstractSetValue<T, Object>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public void setValue(T target, String name, Object setValue)
					throws XMLStreamException {
				((AbstractNamedObjectCollection) target)
						.add((AbstractNamedObject) setValue);
			}
		});
		registerChild(localName, handler);
	}

}
