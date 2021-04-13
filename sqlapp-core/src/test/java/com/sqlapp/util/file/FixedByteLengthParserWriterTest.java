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
/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.PaddingType;

class FixedByteLengthParserWriterTest {

	@Test
	void test() throws IOException {
		final File file=File.createTempFile("text", ".dat");
		final Charset charset=Charset.forName("UTF-8");
		final Table table=createTable();
		try(FixedByteLengthWriter writer=new FixedByteLengthWriter(file, charset, table, setting->{
		}, fieldSetting->{
			if ("a2".equalsIgnoreCase(fieldSetting.getName())) {
				fieldSetting.setPaddingType(PaddingType.LEFT);
			}
		})){
			writer.write(table);
		}
		final String text=FileUtils.readText(file, charset);
		System.out.println(text);
		System.out.println("===========================");
		try(FixedByteLengthParser parser=new FixedByteLengthParser(file, charset, table, setting->{
		}, fieldSetting->{
			if ("a2".equalsIgnoreCase(fieldSetting.getName())) {
				fieldSetting.setPaddingType(PaddingType.LEFT);
			}
		})){
			final long[] counter=new long[1];
			parser.readAllRecord((r,i)->{
				if (i==0) {
					assertEquals("a1_1_val", r.get("a1"));
					assertEquals("a2_1_value", r.get("a2"));
					assertEquals(Integer.valueOf(1), r.get("a3"));
				}else if (i==1) {
					assertEquals("a1_2_val", r.get("a1"));
					assertEquals("a2_2_あ", r.get("a2"));
					assertEquals(Integer.valueOf(2), r.get("a3"));
				}
				counter[0]++;
			});
			assertEquals(2l, counter[0]);
		}
	}

	Table createTable() {
		final Table table=new Table();
		table.getColumns().add(c->{
			c.setName("a1");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		table.getColumns().add(c->{
			c.setName("a2");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.getColumns().add(c->{
			c.setName("a3");
			c.setDataType(DataType.INT);
		});
		table.getRows().add(row->{
			row.put("a1", "a1_1_val");
			row.put("a2", "a2_1_value");
			row.put("a3", 1);
		});
		table.getRows().add(row->{
			row.put("a1", "a1_2_val");
			row.put("a2", "a2_2_あ");
			row.put("a3", 2);
		});
		return table;
	}
	
}
