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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.upperMap;

import java.util.Map;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.AbstractStaxElementHandler;
import com.sqlapp.util.xml.EmptyTextSkipHandler;
import com.sqlapp.util.xml.NotEmptyTextHandler;
import com.sqlapp.util.xml.SetValue;
import com.sqlapp.util.xml.StaxElementHandler;
import com.sqlapp.util.xml.TransparentHandler;

abstract class AbstractObjectXmlReaderHandler<T> extends
		AbstractStaxElementHandler {

	protected Map<String, SetValue<T, ?>> setValueMap = upperMap();

	private Supplier<T> supplier;
	
	private XmlReaderOptions readerOptions=new XmlReaderOptions();
	
	protected AbstractObjectXmlReaderHandler() {
		initializeSetValue();
		registerChild(new EmptyTextSkipHandler());
	}

	protected AbstractObjectXmlReaderHandler(Supplier<T> supplier) {
		this.supplier=supplier;
		initializeSetValue();
		registerChild(new EmptyTextSkipHandler());
	}
	
	protected void initializeSetValue() {
	}

	protected T createNewInstance(){
		return supplier.get();
	}
	
	protected void register(String name, SetValue<T, ?> setValue) {
		setValueMap.put(name, setValue);
	}

	protected void registerTextHandler(String name, SetValue<T, ?> setValue) {
		setValueMap.put(name, setValue);
		registerTransparent(name, new NotEmptyTextHandler());
	}

	protected void registerTextHandler(ISchemaProperty schemaProperty) {
		setValueMap.put(schemaProperty.getLabel(), (target, name,value)->schemaProperty.setValue(target, value));
		registerTransparent(schemaProperty.getLabel(), new NotEmptyTextHandler());
	}


	/**
	 * @return the readerOptions
	 */
	public XmlReaderOptions getReaderOptions() {
		if (this.getParent() instanceof AbstractObjectXmlReaderHandler){
			return ((AbstractObjectXmlReaderHandler<?>)this.getParent()).getReaderOptions();
		}
		return readerOptions;
	}

	/**
	 * @param readerOptions the readerOptions to set
	 */
	public void setReaderOptions(XmlReaderOptions readerOptions) {
		if (readerOptions!=null){
			this.readerOptions = readerOptions;
		}
	}

	protected boolean isDbObject(Class<?> clazz){
		if (DbInfo.class.equals(clazz)){
			return true;
		}
		if (DbCommonObject.class.isAssignableFrom(clazz)){
			return true;
		}
		if (clazz.isArray()){
			if (DbCommonObject.class.isAssignableFrom(clazz.getComponentType())){
				return true;
			}
		}
		return false;
	}
	
	protected void registerTransparent(final String localName,
			StaxElementHandler... childs) {
		TransparentHandler elementHandler = new TransparentHandler(childs) {
			@Override
			public String getLocalName() {
				return localName;
			}
		};
		registerChild(elementHandler);
	}

	protected void setAlias(String name, String alias) {
		SetValue<T, ?> setValue = setValueMap.get(name);
		if (setValue != null) {
			register(alias, setValue);
		}
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

	protected void finishDoHandle(StaxReader reader, Object parentObject,
			T ownObject) {

	}

	protected abstract T createNewInstance(Object parentObject);

	protected void setValue(T obj, String key, Object value)
			throws XMLStreamException {
		SetValue<T, Object> setValue = cast(setValueMap.get(key));
		if (setValue != null && value != null) {
			setValue.setValue(obj, key, value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.AbstractStaxElementHandler#doCallback(com.sqlapp.
	 * util.StaxReader, com.sqlapp.util.xml.StaxElementHandler,
	 * java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		setValue((T) ownObject, name, childObject);
	}

	static class ChildObjectHolder {
		private final Object value;

		ChildObjectHolder(Object value) {
			this.value = value;
		}

		/**
		 * @return the value
		 */
		@SuppressWarnings("unchecked")
		public <T> T getValue() {
			return (T) value;
		}

	}
}
