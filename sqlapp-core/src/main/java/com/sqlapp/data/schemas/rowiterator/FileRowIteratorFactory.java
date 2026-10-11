/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

/** Creates row iterator handlers from data files. */
public final class FileRowIteratorFactory {

	private FileRowIteratorFactory() {
	}

	public static boolean supports(final File file) {
		return file != null && DataFormat.parse(file) != null;
	}

	public static boolean supports(final Path path) {
		return path != null && DataFormat.parse(path) != null;
	}

	public static RowIteratorHandler create(final List<File> files) {
		return createCombined(files, FileRowIteratorFactory::create);
	}

	public static RowIteratorHandler create(final File file) {
		final DataFormat format = requireFormat(file);
		return format.createRowIteratorHandler(file);
	}

	public static RowIteratorHandler create(final Path path) {
		final DataFormat format = requireFormat(path);
		return format.createRowIteratorHandler(path);
	}

	public static RowIteratorHandler create(final List<File> files, final String csvEncoding,
			final int csvSkipHeaderRowsSize, final int excelSkipHeaderRowsSize,
			final JsonConverter jsonConverter, final YamlConverter yamlConverter,
			final TomlConverter tomlConverter, final RowValueConverter valueConverter) {
		return createCombined(files, file -> create(file, csvEncoding,
				csvSkipHeaderRowsSize, excelSkipHeaderRowsSize, jsonConverter, yamlConverter,
				tomlConverter, valueConverter));
	}

	public static RowIteratorHandler create(final File file, final String csvEncoding,
			final int csvSkipHeaderRowsSize, final int excelSkipHeaderRowsSize,
			final JsonConverter jsonConverter, final YamlConverter yamlConverter,
			final TomlConverter tomlConverter, final RowValueConverter valueConverter) {
		final DataFormat format = requireFormat(file);
		return format.createRowIteratorHandler(file, csvEncoding, csvSkipHeaderRowsSize,
				excelSkipHeaderRowsSize, jsonConverter, yamlConverter, tomlConverter, valueConverter);
	}

	private static DataFormat requireFormat(final File file) {
		Objects.requireNonNull(file, "file");
		final DataFormat format = DataFormat.parse(file);
		if (format == null) {
			throw new IllegalArgumentException("Unsupported data file format: " + file);
		}
		return format;
	}

	private static DataFormat requireFormat(final Path path) {
		Objects.requireNonNull(path, "path");
		final DataFormat format = DataFormat.parse(path);
		if (format == null) {
			throw new IllegalArgumentException("Unsupported data file format: " + path);
		}
		return format;
	}

	private static RowIteratorHandler createCombined(final List<File> files,
			final Function<File, RowIteratorHandler> factory) {
		Objects.requireNonNull(files, "files");
		final List<RowIteratorHandler> handlers = files.stream().map(factory).toList();
		return handlers.size() == 1 ? handlers.get(0) : new CombinedRowIteratorHandler(handlers);
	}
}
