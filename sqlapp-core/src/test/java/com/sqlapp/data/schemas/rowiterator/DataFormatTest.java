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

import org.junit.jupiter.api.Test;

class DataFormatTest {

	@Test
	void testParse() {
		parseFileTest("aaaa.xls", DataFormat.EXCEL2003);
		parseFileTest("aaaa.xlsx", DataFormat.EXCEL);
		parseFileTest("aaaa.json", DataFormat.JSON);
		parseFileTest("aaaa.jsonl", DataFormat.JSONL);
		parseFileTest("aaaa.yaml", DataFormat.YAML);
		parseFileTest("aaaa.yml", DataFormat.YAML);
		parseFileTest("aaaa.csv", DataFormat.CSV);
		parseFileTest("aaaa.tsv", DataFormat.TSV);
		parseFileTest("xls", DataFormat.EXCEL2003);
		parseFileTest("xlsx", DataFormat.EXCEL);
		parseFileTest("excel", DataFormat.EXCEL);
		parseFileTest("json", DataFormat.JSON);
		parseFileTest("tsv", DataFormat.TSV);
	}

	private void parseFileTest(String text, DataFormat enm) {
		assertEquals(enm, DataFormat.parse(text));
	}

}
