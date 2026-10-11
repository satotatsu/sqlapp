/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

class DataFormatRowIteratorHandlerTest {

	@Test
	void createsHandlerForEveryDataFormat() {
		assertInstanceOf(CsvRowIteratorHandler.class, DataFormat.CSV.createRowIteratorHandler(new File("items.csv")));
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

	@Test
	void rejectsMissingFileAtCreationTime() {
		assertThrows(NullPointerException.class, () -> DataFormat.JSON.createRowIteratorHandler((File) null));
	}

	@Test
	void createsSingleAndCombinedHandlersFromFiles() {
		assertInstanceOf(CsvRowIteratorHandler.class, FileRowIteratorFactory.create(new File("items.csv")));
		assertInstanceOf(JsonRowIteratorHandler.class, FileRowIteratorFactory.create(Path.of("items.json")));
		assertInstanceOf(YamlRowIteratorHandler.class,
				DataFormat.YAML.createRowIteratorHandler(Path.of("items.yaml")));
		assertInstanceOf(CombinedRowIteratorHandler.class,
				FileRowIteratorFactory.create(List.of(new File("first.csv"), new File("second.json"))));
	}

	@Test
	void allowsAnEmptyFileListAsAnEmptyDataSet() {
		final var handler = FileRowIteratorFactory.create(List.of());
		assertInstanceOf(CombinedRowIteratorHandler.class, handler);
		assertFalse(handler.iterator(new com.sqlapp.data.schemas.Table("ITEMS").getRows()).hasNext());
	}

	@Test
	void rejectsUnknownFileFormat() {
		org.junit.jupiter.api.Assertions.assertTrue(FileRowIteratorFactory.supports(new File("items.toml")));
		org.junit.jupiter.api.Assertions.assertFalse(FileRowIteratorFactory.supports(new File("items.unknown")));
		org.junit.jupiter.api.Assertions.assertFalse(FileRowIteratorFactory.supports((File) null));
		org.junit.jupiter.api.Assertions.assertFalse(FileRowIteratorFactory.supports((Path) null));
		assertThrows(IllegalArgumentException.class,
				() -> FileRowIteratorFactory.create(new File("items.unknown")));
	}

	@Test
	void rejectsNullInputsAtCreationTime() {
		assertThrows(NullPointerException.class, () -> FileRowIteratorFactory.create((File) null));
		assertThrows(NullPointerException.class, () -> FileRowIteratorFactory.create((Path) null));
		assertThrows(NullPointerException.class, () -> FileRowIteratorFactory.create((List<File>) null));
		assertThrows(NullPointerException.class,
				() -> FileRowIteratorFactory.create(java.util.Arrays.asList(new File("items.csv"), null)));
	}

	private static Object create(final DataFormat format) {
		return format.createRowIteratorHandler(new File("items." + format.getFileExtension()), "UTF-8", 1, 2,
				new JsonConverter(), new YamlConverter(), new TomlConverter(), (row, column, value) -> value);
	}
}
