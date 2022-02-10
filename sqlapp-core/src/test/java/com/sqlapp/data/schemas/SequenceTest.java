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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;

public class SequenceTest extends AbstractDbObjectTest<Sequence> {

	public static Sequence getSequence(String name) {
		Sequence sequence = new Sequence(name);
		sequence.setIncrementBy(2);
		sequence.setLastValue(999999);
		sequence.setStartValue(1);
		sequence.setMaxValue(88888);
		sequence.setMinValue(3);
		sequence.setCacheSize(10);
		sequence.setCycle(true);
		return sequence;
	}

	@Override
	protected Sequence getObject() {
		return getSequence("SequenceA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Sequence> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Test
	public void testSequence() {
		Sequence sequence = new Sequence("TEST");
		sequence.setDataType(DataType.TINYINT);
		sequence.setIncrementBy(1);
		sequence.setMinValue(Short.MIN_VALUE);
		assertEquals(BigInteger.valueOf(Byte.MIN_VALUE), sequence.getMinValue());
		//
		sequence.setMaxValue(Short.MAX_VALUE);
		assertEquals(BigInteger.valueOf(Byte.MAX_VALUE), sequence.getMaxValue());
	}

	@Override
	protected void testDiffString(Sequence obj1, Sequence obj2) {
		obj2.setIncrementBy(3);
		obj2.setLastValue(9);
		obj2.setStartValue(9);
		obj2.setMaxValue(8);
		obj2.setMinValue(4);
		obj2.setCacheSize(11);
		obj2.setCycle(false);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
