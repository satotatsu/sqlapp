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

public class CheckConstraintTest extends AbstractDbObjectTest<CheckConstraint> {
	@org.junit.jupiter.api.Test
	public void preservesMultilineExpressionThroughXml() throws Exception {
		final var table = new Table("Orders");
		final String expression = "quantity > 0 -- keep this comment\r\n\tAND label <> '𠮷'\nAND quantity < 10";
		table.getConstraints().add(new CheckConstraint("valid", expression));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		org.junit.jupiter.api.Assertions.assertEquals(expression,
				((CheckConstraint) restored.getConstraints().get(0)).getExpression());
	}

	@Override
	protected CheckConstraint getObject() {
		CheckConstraint cc = new CheckConstraint();
		cc.setName("CKNAME");
		Table table = new Table("table1");
		Column column = new Column("A");
		table.getColumns().add(column);
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
