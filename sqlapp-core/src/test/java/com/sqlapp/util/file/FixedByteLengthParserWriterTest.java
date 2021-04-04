/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.FileUtils;

class FixedByteLengthParserWriterTest {

	@Test
	void test() throws IOException {
		final File file=File.createTempFile("text", ".dat");
		final Charset charset=Charset.forName("UTF-8");
		final Table table=createTable();
		try(FixedByteLengthWriter writer=new FixedByteLengthWriter(file, charset, table, setting->{
		})){
			writer.write(table);
		}
		final String text=FileUtils.readText(file, charset);
		System.out.println(text);
		System.out.println("===========================");
		try(FixedByteLengthParser parser=new FixedByteLengthParser(file, charset, setting->{
			setting.addField(table, fieldSetting->{
				
			});
		})){
			parser.readAllRecord((r,i)->{
				
			});
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
			row.put("a2", "a2_2_value");
			row.put("a3", 2);
		});
		return table;
	}
	
}
