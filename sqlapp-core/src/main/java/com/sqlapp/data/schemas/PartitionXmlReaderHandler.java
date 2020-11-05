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

import com.sqlapp.util.StaxReader;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * パーティションのXML読み込み
 * 
 * @author satoh
 * 
 */
class PartitionXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<Partition> {

	public PartitionXmlReaderHandler() {
		super(()->new Partition());
	}

	@Override
	public void doCallback(StaxReader reader, StaxElementHandler child,
			String name, Object ownObject, Object childObject)
			throws XMLStreamException {
		setValue((Partition) ownObject, name, childObject);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.dataset.AbstractSchemaHandler#createNewInstance(java.lang.
	 * Object)
	 */
	@Override
	protected Partition createNewInstance(Object parentObject) {
		Partition result = new Partition();
		PartitionCollection partitionCollection = getPartitionCollection(parentObject);
		result.setParent(partitionCollection);
		return result;
	}

	protected PartitionCollection getPartitionCollection(Object parentObject) {
		if (parentObject instanceof PartitionCollection) {
			return (PartitionCollection) parentObject;
		} else if (parentObject instanceof Table) {
			Table table = (Table) parentObject;
			if (table.getPartitioning() != null) {
				return table.getPartitioning().getPartitions();
			} else {
				return null;
			}
		} else if (parentObject instanceof Index) {
			Index index = (Index) parentObject;
			if (index.getPartitioning() != null) {
				return index.getPartitioning().getPartitions();
			} else {
				return null;
			}
		} else if (parentObject instanceof Partitioning) {
			return ((Partitioning) parentObject).getPartitions();
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
	protected Partition getInstance(Object parentObject, String name,
			String specificName, String schemaName, Partition obj) {
		PartitionCollection partitionCollection = getPartitionCollection(parentObject);
		if (partitionCollection != null) {
			Partition c = partitionCollection.get(name);
			if (c != null) {
				return c;
			}
		}
		return obj;
	}

	@Override
	protected AbstractSchemaObjectCollection<?> toParent(Object parentObject) {
		return null;
	}
}
