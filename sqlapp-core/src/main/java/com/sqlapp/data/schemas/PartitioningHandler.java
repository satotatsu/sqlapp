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
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * パーティション情報のXML読み込み
 * 
 * @author satoh
 * 
 */
class PartitioningHandler extends AbstractBaseDbObjectXmlReaderHandler<Partitioning> {

	public PartitioningHandler() {
		super(()->new Partitioning());
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		setValue((Partitioning) ownObject, name, childObject);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObjectHandler#createNewInstance(java
	 * .lang.Object)
	 */
	@Override
	protected Partitioning createNewInstance(Object parentObject) {
		Partitioning result = createNewInstance();
		AbstractSchemaObject<?> parent = toParent(parentObject);
		if (parent != null) {
			setParent(result, parent);
		}
		return result;
	}

	protected void setParent(Partitioning t, AbstractSchemaObject<?> parent) {
		t.setParent(parent);
	}

	protected AbstractSchemaObject<?> toParent(Object parentObject) {
		return (AbstractSchemaObject<?>) parentObject;
	}
}
