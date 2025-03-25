package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelUtilsTest {

	@TempDir
	protected File testProjectDir;

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
		try (XSSFWorkbook workbook = new XSSFWorkbook(file)) {
			Sheet sheet = ExcelUtils.getSheet(workbook, "sheet1");
			Row row = sheet.getRow(0);
			Cell cell = row.getCell(0);
			Object obj = ExcelUtils.getCellValue(cell);
			assertEquals(3, obj);
		}
	}

}
