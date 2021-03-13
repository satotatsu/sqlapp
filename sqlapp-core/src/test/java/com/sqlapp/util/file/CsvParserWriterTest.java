/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
			try(CsvWriter writer=new CsvWriter(file, charset, settings->{
			})){
				writer.writeHeader("ah", "bh", "ch", "dh");
				int i=0;
				writer.writeRow("a"+i, "b"+i, "c"+i, "d"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i, "d"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i, "d"+i);
				i++;
				writer.writeRow("a"+i, "b"+i, "c"+i, "d"+i);
				i++;
			}
			final String text=FileUtils.readText(file, charset);
			System.out.println(text);
			System.out.println("===========================");
			try(CsvParser parser=new CsvParser(settings->{
				//settings.setHeaderExtractionEnabled(true);
				settings.selectFields("ah", "bh", "ch");
			})){
				final int[] cnt=new int[1];
				parser.read(file, charset, (row,no)->{
					cnt[0]++;
					assertEquals(3, row.length);
					System.out.println(Arrays.toString(row));
				});
				assertEquals(5, cnt[0]);
			}
		}finally {
			FileUtils.remove(file);
		}
	}

}
