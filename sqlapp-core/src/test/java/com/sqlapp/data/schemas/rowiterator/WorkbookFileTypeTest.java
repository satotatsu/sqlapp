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
