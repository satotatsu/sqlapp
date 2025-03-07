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

import java.io.Serializable;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;

/**
 * マップのEntry読み込みクラス
 * 
 * @author satoh
 * 
 */
public class EntryHandler extends AbstractStaxElementHandler {

	@Override
	public String getLocalName() {
		return StaxWriter.ENTRY_ELEMENT;
	}

	@Override
	protected void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		Entry<String, String> result = new Entry<String, String>();
		if (reader.isStartElement()) {
			int size = reader.getAttributeCount();
			for (int i = 0; i < size; i++) {
				String attributeLocalName = reader.getAttributeLocalName(i);
				String attributeValue = reader.getAttributeValue(i);
				if ("key".equalsIgnoreCase(attributeLocalName)) {
					result.setKey(attributeValue);
				} else if ("value".equalsIgnoreCase(attributeLocalName)) {
					result.setValue(attributeValue);
				}
			}
			reader.next();
			if (reader.isCharacters()) {
				result.setValue(reader.getText());
				reader.next();
			}
		}
		if (match(reader) && reader.isEndElement()) {
			reader.next();
		}
		callParent(reader, getLocalName(), parentObject, result);
	}

	static class Entry<K, V> implements Map.Entry<K, V>, Serializable {
		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;
		private K key;
		private V value;

		public Entry() {
		}

		public Entry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		public void setKey(K key) {
			this.key = key;
		}

		@Override
		public V setValue(V value) {
			this.value = value;
			return null;
		}
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object parentObject, Object value)
			throws XMLStreamException {
	}

}
