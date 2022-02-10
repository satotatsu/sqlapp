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


public class ExcludeConstraintTest extends AbstractDbObjectTest<ExcludeConstraint>{

	@Override
	protected ExcludeConstraint getObject() {
		ExcludeConstraint cc=new ExcludeConstraint();
		cc.setName("EKNAME");
		cc.addColumn("aaaa");
		cc.addColumn("bbbb");
		cc.getColumns().get("aaaa").setWith("&");
		cc.setRemarks("コメント");
		return cc;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Constraint> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ExcludeConstraint obj1, ExcludeConstraint obj2) {
		obj2.setName("b");
		obj2.addColumn("dddd");
		obj2.getColumns().get("aaaa").setWith("&&");
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
