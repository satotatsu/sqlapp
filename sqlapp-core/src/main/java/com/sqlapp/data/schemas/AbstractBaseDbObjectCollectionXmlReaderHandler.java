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

import java.util.Map;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.AbstractSetValue;

abstract class AbstractBaseDbObjectCollectionXmlReaderHandler<T extends AbstractBaseDbObjectCollection<?>>
		extends AbstractObjectXmlReaderHandler<T> {
	
	protected AbstractBaseDbObjectCollectionXmlReaderHandler(Supplier<T> supplier) {
		super(supplier);
	}

	protected void setChild(AbstractBaseDbObjectXmlReaderHandler<?> handler) {
		register(handler.getLocalName(), new AbstractSetValue<T, Object>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public void setValue(T target, String name, Object setValue)
					throws XMLStreamException {
				((AbstractBaseDbObjectCollection) target)
						.add((AbstractBaseDbObject) setValue);
			}
		});
		registerChild(handler);
	}

	protected void setChild(String localName, AbstractBaseDbObjectXmlReaderHandler<?> handler) {
		register(localName, new AbstractSetValue<T, Object>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public void setValue(T target, String name, Object setValue)
					throws XMLStreamException {
				((AbstractBaseDbObjectCollection) target)
						.add((AbstractBaseDbObject) setValue);
			}
		});
		registerChild(localName, handler);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractObjectHandler#doHandle(com.sqlapp.util
	 * .StaxReader, java.lang.Object)
	 */
	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		T obj = null;
		if (parentObject instanceof ChildObjectHolder) {
			obj = ((ChildObjectHolder) parentObject).getValue();
		} else {
			obj = createNewInstance();
		}
		obj = getInstance(parentObject, obj);
		obj.setValidateAtChange(false);
		while (reader.hasNext()) {
			if (reader.isStartElement()) {
				Map<String, String> map = getAttributeMap(reader);
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
		obj.setValidateAtChange(true);
		obj.renew();
		finishDoHandle(reader, parentObject, obj);
		callParent(reader, getLocalName(), parentObject, obj);
	}

	/**
	 * 親のオブジェクトに参照があればそれを返し、無ければ引数で渡されたオブジェクトを返します
	 * 
	 * @param parentObject
	 * @param obj
	 */
	protected T getInstance(Object parentObject, T obj){
		if (instanceGetter!=null){
			T val= instanceGetter.apply(parentObject);
			if (val!=null){
				return val;
			}
		}
		return obj;
	}

	private java.util.function.Function<Object, T> instanceGetter=null;
	
	protected void setInstanceGetter(java.util.function.Function<Object, T> instanceGetter) {
		this.instanceGetter = instanceGetter;
	}

	private final String LOCAL_NAME = createNewInstance().getSimpleName();

	@Override
	public String getLocalName() {
		return LOCAL_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance(java.lang.
	 * Object)
	 */
	@Override
	protected T createNewInstance(Object parentObject) {
		T result = createNewInstance();
		return result;
	}
}
