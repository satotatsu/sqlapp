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

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.xml.NotEmptyTextHandler;
import com.sqlapp.util.xml.StaxElementHandler;
import com.sqlapp.util.xml.StringToStringSetValue;

/**
 * ValueのXML読み込み
 * 
 * @author satoh
 * 
 */
class ValueHolderXmlReaderHandler extends AbstractObjectXmlReaderHandler<ValueHolder> {

	public ValueHolderXmlReaderHandler() {
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		registerTextHandler(StaxWriter.KEY_ELEMENT,
				new StringToStringSetValue<ValueHolder>() {
					@Override
					protected void setValue(ValueHolder target, String val)
							throws XMLStreamException {
						target.setKey(val);
					}
				});
		this.registerChild(new NotEmptyTextHandler());
	}

	@Override
	public String getLocalName() {
		return StaxWriter.VALUE_ELEMENT;
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		setValue((ValueHolder) ownObject, name, childObject);
	}

	@Override
	protected void setValue(ValueHolder obj, String key, Object value)
			throws XMLStreamException {
		if (StaxWriter.KEY_ELEMENT.equals(key)) {
			obj.setKey((String) value);
		}else if (Row.COMMENT.equals(key)) {
			obj.setComment((String) value);
		}else if (Row.OPTION.equals(key)) {
			obj.setOption((String) value);
		} else {
			obj.setValue((String) value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance(java.lang.
	 * Object)
	 */
	@Override
	protected ValueHolder createNewInstance(Object parentObject) {
		ValueHolder valueHolder = new ValueHolder();
		return valueHolder;
	}

}
