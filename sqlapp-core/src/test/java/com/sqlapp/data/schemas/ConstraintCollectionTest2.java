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

import org.junit.jupiter.api.Test;

import com.sqlapp.test.AbstractTest;

public class ConstraintCollectionTest2 extends AbstractTest{

	@Test
	public void test(){
		ConstraintCollection cc1=createConstraintCollection1();
		ConstraintCollection cc2=createConstraintCollection2();
		DbObjectDifferenceCollection diff=cc1.diff(cc2);
		System.out.println(diff);
		assertEquals(this.getResource("constraintCollection.diff"), diff.toString());
	}

	protected ConstraintCollection createConstraintCollection1(){
		ConstraintCollection cc=new ConstraintCollection();
		CheckConstraintTest ccTest=new CheckConstraintTest();
		cc.add(ccTest.getObject().setName("cc0"));
		cc.add(ccTest.getObject().setName("cc1"));
		cc.add(ccTest.getObject().setName("cc4"));
		cc.add(ccTest.getObject().setName("cc6"));
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
		cc.add(ccTest.getObject().setName("cc4"));
		cc.add(ccTest.getObject().setName("cc6"));
		//
		UniqueConstraintTest ucTest=new UniqueConstraintTest();
		cc.add(ucTest.getObject());
		//
		ForeignKeyConstraintTest fcTest=new ForeignKeyConstraintTest();
		cc.add(fcTest.getObject());
		return cc;
	}


}
