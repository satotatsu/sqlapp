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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * サブパーティションのXML読み込み
 * 
 * @author satoh
 * 
 */
class SubPartitionXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<SubPartition> {

	public SubPartitionXmlReaderHandler() {
		super(()->new SubPartition());
	}

	@Override
	protected boolean isAutoRegistProp(ISchemaProperty prop){
		if (prop==SchemaObjectProperties.SUB_PARTITIONS){
			return false;
		}
		return true;
	}
	
	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		setValue((SubPartition) ownObject, name, childObject);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance(java.lang.
	 * Object)
	 */
	@Override
	protected SubPartition createNewInstance(Object parentObject) {
		SubPartition result = createNewInstance();
		SubPartitionCollection partitionCollection = getPartitionCollection(parentObject);
		result.setParent(partitionCollection);
		return result;
	}

	protected SubPartitionCollection getPartitionCollection(Object parentObject) {
		if (parentObject instanceof SubPartitionCollection) {
			return (SubPartitionCollection) parentObject;
		} else if (parentObject instanceof Partition) {
			return ((Partition) parentObject).getSubPartitions();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.xml.AbstractSchemaHandler#getInstance(java.lang.Object
	 * , java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	protected SubPartition getInstance(Object parentObject, String name,
			String specificName, String schemaName, SubPartition obj) {
		SubPartitionCollection partitionCollection = getPartitionCollection(parentObject);
		if (partitionCollection != null) {
			SubPartition c = partitionCollection.get(name);
			if (c != null) {
				return c;
			}
		}
		return obj;
	}

	@Override
	protected SubPartitionCollection toParent(Object parentObject) {
		SubPartitionCollection partitionCollection = getPartitionCollection(parentObject);
		return partitionCollection;
	}
}
