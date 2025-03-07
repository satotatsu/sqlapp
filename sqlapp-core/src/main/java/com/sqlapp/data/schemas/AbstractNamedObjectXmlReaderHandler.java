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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Map;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;

abstract class AbstractNamedObjectXmlReaderHandler<T extends AbstractNamedObject<?>>
		extends AbstractBaseDbObjectXmlReaderHandler<T> {

	protected AbstractNamedObjectXmlReaderHandler(Supplier<T> supplier) {
		super(supplier);
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		T obj = null;
		if (parentObject instanceof ChildObjectHolder) {
			obj = ((ChildObjectHolder) parentObject).getValue();
		} else {
			obj = createNewInstance(parentObject);
		}
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				Map<String, String> map = getAttributeMap(reader);
				String name = map.get(SchemaProperties.NAME.getLabel());
				String specificName = map.get(SchemaProperties.SPECIFIC_NAME.getLabel());
				String schemaName = map.get(SchemaProperties.SCHEMA_NAME.getLabel());
				if (!isEmpty(name)) {
					obj = getInstance(parentObject, name, specificName,
							schemaName, obj);
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

	protected T getInstance(Object parentObject, String name,
			String specificName, String schemaName, T obj) {
		DbCommonObject<?> parentObj = toParent(parentObject);
		if (parentObj instanceof AbstractNamedObjectCollection<?>){
			AbstractNamedObjectCollection<?> parent = (AbstractNamedObjectCollection<?>)parentObj;
			if (parent != null) {
				@SuppressWarnings("unchecked")
				T a = (T) parent.get(specificName);
				if (a != null) {
					return a;
				}
				if (specificName!=null){
					return obj;
				}
				@SuppressWarnings("unchecked")
				T b = (T) parent.get(name);
				if (b != null) {
					return b;
				}
			}
		}
		return obj;
	}
}
