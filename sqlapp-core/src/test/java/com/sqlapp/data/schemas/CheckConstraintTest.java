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


public class CheckConstraintTest extends AbstractDbObjectTest<CheckConstraint>{

	@Override
	protected CheckConstraint getObject() {
		CheckConstraint cc=new CheckConstraint();
		cc.setName("CKNAME");
		Table table=new Table("table1");
		Column column=new Column("A");
		table.getColumns().add(column);
		cc.addColumns(column);
		cc.setExpression("A>2");
		cc.setRemarks("コメント");
		return cc;
	}

	@Override
	protected void testDiffString(CheckConstraint obj1, CheckConstraint obj2) {
		obj2.setName("b");
		obj2.setRemarks("コメントB");
		obj2.setExpression("B>1");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
