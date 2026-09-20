/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.export;

import java.io.File;
import java.util.List;

import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.CombinedRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.CsvRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.JsonLineRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.TomlRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.XmlRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.YamlRowIteratorHandler;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

/** File decoding only; callers retain ownership of expression evaluation and SQL execution. */
final class FileRowIteratorFactory {

	private FileRowIteratorFactory() {
	}

	static RowIteratorHandler create(final List<File> files, final String csvEncoding,
			final int csvSkipHeaderRowsSize, final int excelSkipHeaderRowsSize,
			final JsonConverter jsonConverter, final YamlConverter yamlConverter,
			final TomlConverter tomlConverter, final RowValueConverter valueConverter) {
		final List<RowIteratorHandler> handlers = files.stream().map(file -> create(file, csvEncoding,
				csvSkipHeaderRowsSize, excelSkipHeaderRowsSize, jsonConverter, yamlConverter,
				tomlConverter, valueConverter)).toList();
		return handlers.size() == 1 ? handlers.get(0) : new CombinedRowIteratorHandler(handlers);
	}

	private static RowIteratorHandler create(final File file, final String csvEncoding,
			final int csvSkipHeaderRowsSize, final int excelSkipHeaderRowsSize,
			final JsonConverter jsonConverter, final YamlConverter yamlConverter,
			final TomlConverter tomlConverter, final RowValueConverter valueConverter) {
		final DataFormat format = DataFormat.parse(file);
		if (format == null) {
			throw new IllegalArgumentException("Unsupported data file format: " + file);
		}
		if (format.isCsv()) {
			return new CsvRowIteratorHandler(file, csvEncoding, csvSkipHeaderRowsSize, valueConverter);
		} else if (format.isXml()) {
			return new XmlRowIteratorHandler(file, valueConverter);
		} else if (format.isToml()) {
			return new TomlRowIteratorHandler(file, tomlConverter, valueConverter);
		} else if (format.isYaml()) {
			return new YamlRowIteratorHandler(file, yamlConverter, valueConverter);
		} else if (format.isJsonl()) {
			return new JsonLineRowIteratorHandler(file, jsonConverter, valueConverter);
		} else if (format.isTextFile()) {
			return new JsonRowIteratorHandler(file, jsonConverter, valueConverter);
		} else {
			return new ExcelRowIteratorHandler(file, excelSkipHeaderRowsSize, valueConverter);
		}
	}
}
