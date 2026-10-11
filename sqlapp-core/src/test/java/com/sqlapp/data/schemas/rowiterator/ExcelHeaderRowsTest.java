/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ExcelHeaderRowsTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void honorsHeaderCountAndPreservesSchemaTypes(final int headers) throws Exception {
		final Path file = workbook(headers);
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.setRowIteratorHandler(new ExcelRowIteratorHandler(file.toFile(), headers));
		int count = 0;
		for (var row : table.getRows()) {
			assertEquals(42, ((Number) row.get("ID")).intValue());
			assertEquals(headers + 1, row.getDataSourceRowNumber().intValue());
			count++;
		}
		assertEquals(1, count);
		assertEquals(DataType.INT, table.getColumns().get("ID").getDataType());
	}

	@Test
	void rejectsPositionalInputWithoutSchema() throws Exception {
		final Table table = new Table("ITEMS");
		table.setRowIteratorHandler(new ExcelRowIteratorHandler(workbook(0).toFile(), 0));
		assertThrows(RuntimeException.class, () -> table.getRows().iterator().hasNext());
	}

	@Test
	void rejectsNegativeHeaderCount() throws Exception {
		final Table table = new Table("ITEMS");
		final var handler = new ExcelRowIteratorHandler(workbook(0).toFile(), -1);
		assertThrows(IllegalArgumentException.class, () -> handler.iterator(table.getRows()));
	}

	@Test
	void declaredExcel2003FormatDoesNotDependOnFileExtension() throws Exception {
		final Path file = directory.resolve("items.data");
		try (var workbook = new HSSFWorkbook(); var output = Files.newOutputStream(file)) {
			final var sheet = workbook.createSheet("items");
			sheet.createRow(0).createCell(0).setCellValue("ID");
			sheet.createRow(1).createCell(0).setCellValue(42);
			workbook.write(output);
		}
		final Table table = new Table("ITEMS");
		table.setRowIteratorHandler(DataFormat.EXCEL2003.createRowIteratorHandler(file));

		final var iterator = table.getRows().iterator();
		assertTrue(iterator.hasNext());
		assertEquals(42, ((Number) iterator.next().get("ID")).intValue());
		assertFalse(iterator.hasNext());
	}

	@Test
	void explicitCloseReportsWorkbookCloseFailure() {
		final IOException closeFailure = new IOException("close failed");
		final Table table = new Table("ITEMS");
		final var iterator = new ExcelRowIteratorHandler.ExcelIterator(table.getRows(),
				new java.io.File("items.xlsx"), 0L, (row, column, value) -> value, 1) {
			@Override
			protected void closeWorkbook() throws IOException {
				throw closeFailure;
			}
		};

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::close);
		assertSame(closeFailure, thrown.getCause());
	}

	private Path workbook(final int headers) throws Exception {
		final Path file = directory.resolve("items.xlsx");
		try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(file)) {
			final var sheet = workbook.createSheet("items");
			for (int i = 0; i < headers; i++) {
				sheet.createRow(i).createCell(0).setCellValue(headers == 1 ? "ID" : "comment");
			}
			sheet.createRow(headers).createCell(0).setCellValue(42);
			workbook.write(output);
		}
		return file;
	}
}
