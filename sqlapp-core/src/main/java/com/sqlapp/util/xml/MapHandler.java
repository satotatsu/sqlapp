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
import static com.sqlapp.util.CommonUtils.linkedMap;

import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;

/**
 * マップの読み込みクラス
 * 
 * @author satoh
 * 
 */
public class MapHandler extends AbstractCollectionHandler<Map<String, String>> {

	@Override
	public String getLocalName() {
		return StaxWriter.MAP_ELEMENT;
	}

	public MapHandler() {
		this.registerChild(new EntryHandler());
		this.registerChild(new EmptyTextSkipHandler());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.xml.AbstractStaxElementHandler#doCallback(com.sqlapp.
	 * util.StaxReader, com.sqlapp.util.xml.StaxElementHandler,
	 * java.lang.String, java.lang.Object, java.lang.Object)
	 */
	@Override
	protected void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		Map<String, String> map = cast(ownObject);
		if (childObject != null) {
			Entry<String, String> entry = cast(childObject);
			map.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	protected Map<String, String> createNewInstance() {
		return linkedMap();
	}
}
