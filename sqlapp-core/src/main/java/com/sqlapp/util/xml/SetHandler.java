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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.linkedSet;

import java.util.Set;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;

/**
 * セットの読み込みクラス
 * 
 * @author satoh
 * 
 */
public class SetHandler extends AbstractCollectionHandler<Set<String>> {

	@Override
	public String getLocalName() {
		return StaxWriter.SET_ELEMENT;
	}

	public SetHandler() {
		this.registerChild(new ValueHandler());
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
		Set<String> result = cast(ownObject);
		result.add((String) childObject);
	}

	@Override
	protected Set<String> createNewInstance() {
		return linkedSet();
	}
}
