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


public class RolePrivilegeTest extends AbstractDbObjectTest<RolePrivilege>{

	public static RolePrivilege getPrivilege(){
		RolePrivilege privilege=new RolePrivilege();
		privilege.setGranteeName("grantee").setGrantorName("dbo").setPrivilege("SELECT").setAdmin(true);
		return privilege;
	}

	@Override
	protected RolePrivilege getObject() {
		return getPrivilege();
	}

	@Override
	protected AbstractBaseDbObjectXmlReaderHandler<RolePrivilege> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(RolePrivilege obj1, RolePrivilege obj2) {
		obj2.setGranteeName("grantee1").setGrantorName("dbo1").setPrivilege("UPDATE").setAdmin(false);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
