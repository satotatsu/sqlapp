/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.FileUtils;

class CsvParserWriterTest {

	@Test
	void test() throws IOException {
		final File file=File.createTempFile("text", ".csv");
		final Charset charset=Charset.forName("UTF-8");
		try{
			try(CsvWriter writer=new CsvWriter(file, charset, setting->{
			})){
				writer.writeHeader("ah", "bh", "ch");
				int i=0;
				writer.writeRow("a"+i, "b"+i, "c"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i);
				i++;
			}
			final String text=FileUtils.readText(file, charset);
			System.out.println(text);
			try(CsvParser parser=new CsvParser(setting->{
			})){
				parser.read(file, charset, (row,no)->{
					System.out.println(Arrays.toString(row));
				});
			}
		}finally {
			FileUtils.remove(file);
		}
	}

}
