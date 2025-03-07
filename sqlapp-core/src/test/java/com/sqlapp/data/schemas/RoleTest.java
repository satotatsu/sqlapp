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


public class RoleTest extends AbstractDbObjectTest<Role>{

	public static Role getRole(){
		Role role=new Role();
		role.setName("role1").setGrantable(true);
		return role;
	}

	@Override
	protected Role getObject() {
		return getRole();
	}

	@Override
	protected AbstractBaseDbObjectXmlReaderHandler<Role> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Role obj1, Role obj2) {
		obj2.setGrantable(false);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
