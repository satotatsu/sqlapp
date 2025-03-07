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

public class PublicDbLinkTest extends AbstractDbObjectTest<PublicDbLink> {

	public static PublicDbLink getDbLink(String name) {
		PublicDbLink obj = new PublicDbLink(name);
		obj.setDriverClassName("driverClass1");
		obj.setUserId("userId1");
		obj.setPassword("pass1");
		return obj;
	}

	@Override
	protected PublicDbLink getObject() {
		return getDbLink("DbLinkA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<PublicDbLink> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(PublicDbLink obj1, PublicDbLink obj2) {
		obj2.setDriverClassName("driverClass2");
		obj2.setUserId("userId2");
		obj2.setPassword("pass2");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
