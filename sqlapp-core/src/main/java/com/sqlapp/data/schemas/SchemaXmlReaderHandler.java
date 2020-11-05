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

/**
 * SchemaのXML読み込み
 * 
 * @author satoh
 * 
 */
class SchemaXmlReaderHandler extends AbstractNamedObjectXmlReaderHandler<Schema> {

	public SchemaXmlReaderHandler() {
		super(()->new Schema());
	}

	@Override
	protected Schema createNewInstance(Object parentObject) {
		Schema schema = new Schema();
		if (parentObject instanceof SchemaCollection) {
			SchemaCollection schemas = (SchemaCollection) parentObject;
			schema.setSchemas(schemas);
		}
		return schema;
	}

	@Override
	protected Schema getInstance(Object parentObject, String name,
			String specificName, String schemaName, Schema obj) {
		if (parentObject instanceof SchemaCollection) {
			SchemaCollection schemas = (SchemaCollection) parentObject;
			Schema schema = schemas.get(name);
			if (schema != null) {
				return schema;
			}
		}
		return obj;
	}

	@Override
	protected SchemaCollection toParent(Object parentObject) {
		SchemaCollection parent = null;
		if (parentObject instanceof Catalog) {
			parent = ((Catalog) parentObject).getSchemas();
		} else if (parentObject instanceof SchemaCollection) {
			parent = (SchemaCollection) parentObject;
		}
		return parent;
	}

	@Override
	protected Schema createNewInstance() {
		return new Schema();
	}
	
	@Override
	protected void callParent(StaxReader reader, String name,
			Object parentObject, Object value) throws XMLStreamException {
		if (value instanceof Schema){
			((Schema)value).validate();
		}
		super.callParent(reader, name, parentObject, value);
	}

}
