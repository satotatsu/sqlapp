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
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Beanユーティリティのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SimpleBeanUtilsTest3 {

	@Test
	public void testtoTable1() {
		final Table table = SimpleBeanUtils.toTable(DummyClass.class, col->CommonUtils.eq("id",col.getName()), false);
		assertEquals("DUMMY_CLASS", table.getName());
		assertEquals(11, table.getColumns().size());
		int i=0;
		Column column=table.getColumns().get(i++);
		assertEquals("id", column.getName());
		assertEquals(true, column.isNotNull());
		assertEquals(true, column.isIdentity());
		assertEquals(DataType.INT, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("name", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(16, column.getLength());
		assertEquals(DataType.VARCHAR, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("VAL1", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(128, column.getLength());
		assertEquals(DataType.VARCHAR, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("enable", column.getName());
		assertEquals(true, column.isNotNull());
		assertEquals(DataType.BOOLEAN, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("enableWrapper", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(DataType.BOOLEAN, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("date", column.getName());
		assertEquals(true, column.isNotNull());
		assertEquals(DataType.DATETIME, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("sqlDate", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(DataType.DATE, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("byteData", column.getName());
		assertEquals(true, column.isNotNull());
		assertEquals(DataType.TINYINT, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("binaryData", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(DataType.VARBINARY, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("uuid", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(DataType.UUID, column.getDataType());
		//
		column=table.getColumns().get(i++);
		assertEquals("dec_1", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(DataType.DECIMAL, column.getDataType());
		//
		i=0;
		UniqueConstraint uc=table.getConstraints().getUniqueConstraints().get(i++);
		assertEquals("id", uc.getColumns().get(0).getName());
		uc=table.getConstraints().getUniqueConstraints().get(i++);
		assertEquals("VAL1", uc.getColumns().get(0).getName());
	}

	@javax.persistence.Table(name="DUMMY_CLASS")
	static class DummyClass {
		@GeneratedValue(strategy=GenerationType.IDENTITY)
		public int id;
		@Size(max=16)
		public String name;
		@javax.persistence.Column(name="VAL1", length=128, unique=true)
		public String value;
		public boolean enable;
		public Boolean enableWrapper;
		@NotNull
		public Date date;
		public java.sql.Date sqlDate;
		public byte byteData;
		public byte[] binaryData;
		public UUID uuid;
		@javax.persistence.Column(name="dec_1")
		public BigDecimal dec;
	}

}
