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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.CommonUtils.upperMap;

import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.ToStringBuilder;

public abstract class AbstractStaxElementHandler implements StaxElementHandler {

	private String namespaceURI = null;
	private StaxElementHandler parent = null;

	private StaxElementHandler[] childs = new StaxElementHandler[0];

	private Map<String, StaxElementHandler> childMap = upperMap();

	protected AbstractStaxElementHandler() {
	}

	/**
	 * XMLのハンドルメソッド
	 */
	@Override
	public boolean handle(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		if (!match(reader)) {
			return false;
		}
		if (reader.isEndDocument()) {
			return true;
		}
		if (reader.isStartDocument()) {
			reader.next();
		}
		doHandle(reader, parentObject);
		return true;
	}

	protected void callChilds(StaxReader reader, Object ownObject)
			throws XMLStreamException {
		while (reader.hasNext()) {
			callChild(reader, ownObject);
			if (match(reader) && reader.isEndElement()) {
				break;
			}
		}
	}

	/**
	 * 子供の要素の読み込み
	 * 
	 * @param reader
	 * @throws XMLStreamException
	 */
	private void callChild(StaxReader reader, Object parentObject)
			throws XMLStreamException {
		if (reader.isStartElement()) {
			if (CommonUtils.isEmpty(reader.getNamespaceURI())) {
				String localName = reader.getLocalName();
				StaxElementHandler child = childMap.get(localName);
				if (child != null) {
					boolean ret = child.handle(reader, parentObject);
					if (ret) {
						return;
					}
				}
			}
		}
		int size = childs.length;
		for (int i = 0; i < size; i++) {
			StaxElementHandler child = childs[i];
			boolean ret = child.handle(reader, parentObject);
			if (ret) {
				return;
			}
		}
		reader.raiseElementException(this.getLocalName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.StaxElementHandler#match(com.sqlapp.util.StaxReader)
	 */
	@Override
	public boolean match(StaxReader reader) {
		if (reader.isStartElement() || reader.isEndElement()) {
			String localName = reader.getLocalName();
			String namespaceURI = reader.getNamespaceURI();
			if (CommonUtils.eq(localName, this.getLocalName())
					&& CommonUtils.eq(CommonUtils.emptyToNull(namespaceURI),
							CommonUtils.emptyToNull(this.getNamespaceURI()))) {
				return true;
			}
			return eqIgnoreCase(localName, this.getLocalName())
					&& eqIgnoreCase(CommonUtils.emptyToNull(namespaceURI),
							CommonUtils.emptyToNull(this.getNamespaceURI()));
		}
		return false;
	}

	protected abstract void doHandle(StaxReader reader, Object parentObject)
			throws XMLStreamException;

	protected void callParent(StaxReader reader, String name,
			Object parentObject, Object value) throws XMLStreamException {
		if (this.getParent() != null) {
			this.getParent().callback(reader, name, parentObject, value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.StaxElementHandler#callback(com.sqlapp.util.StaxReader
	 * , java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void callback(StaxReader reader, String name, Object parentObject,
			Object value) throws XMLStreamException {
		if (value == null) {
			if (!reader.hasNext()) {
				return;
			}
		}
		doCallback(reader, this, name, parentObject, value);
	}

	protected abstract void doCallback(StaxReader reader,
			StaxElementHandler child, String name, Object parentObject,
			Object value) throws XMLStreamException;

	/**
	 * 属性のマップの取得
	 * 
	 * @param reader
	 */
	protected Map<String, String> getAttributeMap(StaxReader reader) {
		int size = reader.getAttributeCount();
		Map<String, String> map = map(size, 1.0f);
		for (int i = 0; i < size; i++) {
			String key = reader.getAttributeLocalName(i);
			String value = reader.getAttributeValue(i);
			map.put(key, value);
		}
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.xml.StaxElementHandler#getNamespaceURI()
	 */
	@Override
	public String getNamespaceURI() {
		return namespaceURI;
	}

	public StaxElementHandler getParent() {
		return parent;
	}

	public void setNamespaceURI(String namespaceURI) {
		this.namespaceURI = namespaceURI;
	}

	public void setParent(StaxElementHandler parent) {
		this.parent = parent;
	}

	protected void registerChild(String localName, String namespaceURI,
			StaxElementHandler child) {
		child.setParent(this);
		if (isEmpty(namespaceURI) && !isEmpty(localName)) {
			childMap.put(localName, child);
		} else {
			List<StaxElementHandler> list = list(this.childs);
			list.add(child);
			this.childs = list.toArray(new StaxElementHandler[0]);
		}
	}

	protected void registerChild(String localName, StaxElementHandler child) {
		registerChild(localName, null, child);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.StaxElementHandler#registerChild(com.sqlapp.util.
	 * xml.StaxElementHandler)
	 */
	@Override
	public void registerChild(StaxElementHandler child) {
		registerChild(child.getLocalName(), child.getNamespaceURI(), child);
	}

	protected void setChilds(StaxElementHandler... childs) {
		List<StaxElementHandler> list = list(this.childs);
		for (StaxElementHandler child : childs) {
			child.setParent(this);
			if (isEmpty(child.getNamespaceURI())
					&& !isEmpty(child.getLocalName())) {
				childMap.put(child.getLocalName(), child);
			} else {
				list.add(child);
			}
		}
		this.childs = list.toArray(new StaxElementHandler[0]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AbstractStaxElementHandler)) {
			return false;
		}
		AbstractStaxElementHandler val = cast(obj);
		if (!eq(this.getLocalName(), val.getLocalName())) {
			return false;
		}
		if (!eq(this.getNamespaceURI(), val.getNamespaceURI())) {
			return false;
		}
		if (!eq(this.getClass(), val.getClass())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return CommonUtils
				.hashCode(this.getLocalName(), this.getNamespaceURI());
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("localName", this.getLocalName());
		return builder.toString();
	}
}
