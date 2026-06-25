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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UniqueConstraintTest extends AbstractDbObjectTest<UniqueConstraint> {

	@Override
	protected UniqueConstraint getObject() {
		UniqueConstraint cc = new UniqueConstraint();
		cc.setName("UKNAME");
		cc.getColumns().addAll(new Column("aaaa"), new Column("bbbb"));
		cc.setRemarks("コメント");
		return cc;
	}

	@Override
	protected UniqueConstraintXmlReaderHandler getHandler() {
		return new UniqueConstraintXmlReaderHandler();
	}

	@Override
	protected void testDiffString(UniqueConstraint obj1, UniqueConstraint obj2) {
		obj2.setName("b");
		obj2.getColumns().add(new Column("dddd"));
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		String expected = """
				C:uniqueConstraint[name=(UKNAME -> b), columns=(
					+:column[name=dddd]
					), remarks=(コメント -> コメントB)]
					""";
		assertEquals(expected.trim(), diff.toString().trim());
	}

}
