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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelUtilsTest {

	@TempDir
	protected File testProjectDir;

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void returnsEvaluatedFormulaValuesInsteadOfStaleCaches(final boolean xlsx) throws Exception {
		try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
			final Row row = workbook.createSheet("items").createRow(0);
			row.createCell(0).setCellValue(1);
			final Cell number = row.createCell(1);
			number.setCellFormula("A1+1");
			final Cell bool = row.createCell(2);
			bool.setCellFormula("A1=2");
			final Cell text = row.createCell(3);
			text.setCellFormula("IF(A1=2,\"new\",\"old\")");
			workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
			row.getCell(0).setCellValue(2);
			assertEquals(2d, number.getNumericCellValue());
			assertEquals(false, bool.getBooleanCellValue());
			assertEquals("old", text.getStringCellValue());
			assertEquals(3, ExcelUtils.getCellValue(number));
			assertEquals(true, ExcelUtils.getCellValue(bool));
			assertEquals("new", ExcelUtils.getCellValue(text));
		}
	}

	@Test
	void test() throws FileNotFoundException, IOException, InvalidFormatException {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Sheet sheet = ExcelUtils.getOrCreateSheet(workbook, "sheet1");
			Row row = ExcelUtils.getOrCreateRow(sheet, 0);
			Cell cell = ExcelUtils.getOrCreateCell(row, 0);
			ExcelUtils.setCell(cell, 3);
			File file = new File(testProjectDir, "sample.xlsx");
			ExcelUtils.writeWorkbook(workbook, file);
		}
		File file = new File(testProjectDir, "sample.xlsx");
		ExcelUtils.readWorkbook(file, workbook -> {
			Sheet sheet = ExcelUtils.getSheet(workbook, "sheet1");
			Row row = sheet.getRow(0);
			Cell cell = row.getCell(0);
			Object obj = ExcelUtils.getCellValue(cell);
			assertEquals(3, obj);
		});
		Files.delete(file.toPath());
	}

}
