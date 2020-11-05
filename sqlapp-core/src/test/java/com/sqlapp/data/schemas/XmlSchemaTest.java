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

public class XmlSchemaTest extends AbstractDbObjectTest<XmlSchema> {

	public static XmlSchema getXmlSchema(String name) {
		XmlSchema obj = new XmlSchema(name);
		return obj;
	}

	@Override
	protected XmlSchema getObject() {
		return getXmlSchema("XmlSchemaA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<XmlSchema> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(XmlSchema obj1, XmlSchema obj2) {
		obj2.setName("XmlSchemaB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
