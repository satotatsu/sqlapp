/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

class DataFormatRowIteratorHandlerTest {

	@Test
	void createsHandlerForEveryDataFormat() {
		assertInstanceOf(CsvRowIteratorHandler.class, create(DataFormat.CSV));
		assertInstanceOf(CsvRowIteratorHandler.class, create(DataFormat.TSV));
		assertInstanceOf(CsvRowIteratorHandler.class, create(DataFormat.SSV));
		assertInstanceOf(XmlRowIteratorHandler.class, create(DataFormat.XML));
		assertInstanceOf(JsonRowIteratorHandler.class, create(DataFormat.JSON));
		assertInstanceOf(JsonLineRowIteratorHandler.class, create(DataFormat.JSONL));
		assertInstanceOf(TomlRowIteratorHandler.class, create(DataFormat.TOML));
		assertInstanceOf(YamlRowIteratorHandler.class, create(DataFormat.YAML));
		assertInstanceOf(ExcelRowIteratorHandler.class, create(DataFormat.EXCEL));
		assertInstanceOf(ExcelRowIteratorHandler.class, create(DataFormat.EXCEL2003));
	}

	private static Object create(final DataFormat format) {
		return format.createRowIteratorHandler(new File("items." + format.getFileExtension()), "UTF-8", 1, 2,
				new JsonConverter(), new YamlConverter(), new TomlConverter(), (row, column, value) -> value);
	}
}
