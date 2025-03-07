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

import static com.sqlapp.util.CommonUtils.cast;

import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.EntryHandler;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * DBInfoの読み込みクラス
 * 
 * @author satoh
 * 
 */
class DbInfoXmlReaderHandler extends AbstractObjectXmlReaderHandler<DbInfo> {

	@Override
	public String getLocalName() {
		return SchemaProperties.SPECIFICS.getLabel();
	}

	protected DbInfoXmlReaderHandler() {
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		this.registerChild(new DbInfoEntryXmlReaderHandler());
		EntryHandler handler = new EntryHandler();
		registerChild(handler);
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		DbInfo dbInfo = cast(ownObject);
		if (childObject != null) {
			if (childObject instanceof DbInfoEntry){
				DbInfoEntry dbInfoEntry = cast(childObject);
				dbInfoEntry.getKeyValues().forEach((k,v)->{
					dbInfo.put(k, v);
				});
			}else{
				Entry<String,String> entry = cast(childObject);
				dbInfo.put(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	protected DbInfo createNewInstance(Object parentObject) {
		AbstractDbObject<?> obj = null;
		if (parentObject instanceof AbstractDbObject<?>) {
			obj = (AbstractDbObject<?>) parentObject;
			if (obj.getSpecifics() != null) {
				return obj.getSpecifics();
			}
		}
		return new DbInfo();
	}
}
