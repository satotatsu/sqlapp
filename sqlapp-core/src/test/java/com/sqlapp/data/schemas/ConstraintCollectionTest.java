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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ConstraintCollectionTest {

	@Test
	public void testFind(){
		ConstraintCollection cc=new ConstraintCollection();
		CheckConstraintTest ccTest=new CheckConstraintTest();
		cc.add(ccTest.getObject());
		//
		UniqueConstraintTest ucTest=new UniqueConstraintTest();
		cc.add(ucTest.getObject());
		//
		ForeignKeyConstraintTest fcTest=new ForeignKeyConstraintTest();
		cc.add(fcTest.getObject());
		//
		assertEquals(ccTest.getObject(), cc.find(ccTest.getObject()));
		assertEquals(ucTest.getObject(), cc.find(ucTest.getObject()));
		assertEquals(fcTest.getObject(), cc.find(fcTest.getObject()));
	}

	@Test
	public void test2(){
		ConstraintCollection cc1=createConstraintCollection1();
		ConstraintCollection cc2=createConstraintCollection2();
		DbObjectDifferenceCollection diff=cc1.diff(cc2);
		System.out.println(diff);
	}

	protected ConstraintCollection createConstraintCollection1(){
		ConstraintCollection cc=new ConstraintCollection();
		CheckConstraintTest ccTest=new CheckConstraintTest();
		cc.add(ccTest.getObject().setName("cc0"));
		cc.add(ccTest.getObject().setName("cc1"));
		//
		UniqueConstraintTest ucTest=new UniqueConstraintTest();
		cc.add(ucTest.getObject());
		//
		ForeignKeyConstraintTest fcTest=new ForeignKeyConstraintTest();
		cc.add(fcTest.getObject());
		return cc;
	}

	protected ConstraintCollection createConstraintCollection2(){
		ConstraintCollection cc=new ConstraintCollection();
		CheckConstraintTest ccTest=new CheckConstraintTest();
		cc.add(ccTest.getObject().setName("cc1"));
		cc.add(ccTest.getObject().setName("cc2"));
		cc.add(ccTest.getObject().setName("cc3"));
		//
		UniqueConstraintTest ucTest=new UniqueConstraintTest();
		cc.add(ucTest.getObject());
		//
		ForeignKeyConstraintTest fcTest=new ForeignKeyConstraintTest();
		cc.add(fcTest.getObject());
		return cc;
	}


}
