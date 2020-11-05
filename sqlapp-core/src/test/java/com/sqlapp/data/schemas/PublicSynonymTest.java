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

public class PublicSynonymTest extends AbstractDbObjectTest<PublicSynonym> {

	public static PublicSynonym getSynonym(String name) {
		PublicSynonym obj = new PublicSynonym(name);
		obj.setObjectName("obj1");
		obj.setObjectSchemaName("objSchema1");
		obj.setDbLinkName("dblink1");
		return obj;
	}

	@Override
	protected PublicSynonym getObject() {
		return getSynonym("SynonymA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<PublicSynonym> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(PublicSynonym obj1, PublicSynonym obj2) {
		obj2.setObjectName("obj2");
		obj2.setObjectSchemaName("objSchema2");
		obj2.setDbLinkName("dblink2");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
