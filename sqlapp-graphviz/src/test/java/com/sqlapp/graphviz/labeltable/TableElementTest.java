/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz.labeltable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.graphviz.AbstractTest;

public class TableElementTest extends AbstractTest{

	@Test
	public void test1() {
		TableElement tableElement=new TableElement();
		assertEquals(this.getResource("table1.txt"), tableElement.toString());
	}

	@Test
	public void test2() {
		TableElement tableElement=new TableElement();
		tableElement.addRows((tr, i)->{
			
		}, 5);
		assertEquals(this.getResource("table2.txt"), tableElement.toString());
	}
	
	@Test
	public void test3() {
		TableElement tableElement=new TableElement();
		tableElement.addRows((tr, i)->{
			tr.addCells((cell,j)->{
			}, 3);
		}, 5);
		assertEquals(this.getResource("table3.txt"), tableElement.toString());
	}
	
	@Test
	public void test4() {
		TableElement tableElement=new TableElement();
		tableElement.addRows((tr, i)->{
			tr.addCells((cell,j)->{
				cell.setValue(""+i+"_"+j);
			}, 3);
		}, 5);
		assertEquals(this.getResource("table4.txt"), tableElement.toString());
	}
}
