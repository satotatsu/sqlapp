package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

class WorkbookFileTypeTest {

	@Test
	void testParse() {
		parseFileTest("aaaa.xls", WorkbookFileType.EXCEL2003);
		parseFileTest("aaaa.xlsx", WorkbookFileType.EXCEL2007);
		parseFileTest("aaaa.json", WorkbookFileType.JSON);
		parseFileTest("aaaa.jsonl", WorkbookFileType.JSONL);
		parseFileTest("aaaa.yaml", WorkbookFileType.YAML);
		parseFileTest("aaaa.yml", WorkbookFileType.YAML);
		parseFileTest("aaaa.csv", WorkbookFileType.CSV);
		parseFileTest("aaaa.tsv", WorkbookFileType.TSV);
	}

	private void parseFileTest(String filename, WorkbookFileType enm) {
		File file = new File(filename);
		assertEquals(enm, WorkbookFileType.parse(file));
	}

}
