/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.iterable;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;

/**
 * Reads a JSON array whose elements are row objects.
 */
public class JsonMapIterable extends AbstractMapIterable {

	private final JsonConverter converter;

	public JsonMapIterable(final File file) {
		super(file);
		this.converter = new JsonConverter();
	}

	public JsonMapIterable(final Path path) {
		super(path);
		this.converter = new JsonConverter();
	}

	public JsonMapIterable(final InputStream inputStream) {
		super(inputStream);
		this.converter = new JsonConverter();
	}

	public JsonMapIterable(final Reader reader) {
		super(reader);
		this.converter = new JsonConverter();
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final File file) {
		return getRows(converter.fromJsonString(file, Object.class), file.getAbsolutePath()).iterator();
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final Path path) {
		return iterator(path.toFile());
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final InputStream inputStream) {
		return getRows(converter.fromJsonString(inputStream, Object.class), "input stream").iterator();
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final Reader reader) {
		return getRows(converter.fromJsonString(FileUtils.read(reader), Object.class), "reader").iterator();
	}

	/**
	 * Validates and returns the row objects in a parsed JSON array.
	 */
	public static List<Map<String, Object>> getRows(final Object value, final String source) {
		if (!(value instanceof List<?> values)) {
			throw invalidJson(source);
		}
		final List<Map<String, Object>> result = new ArrayList<>(values.size());
		for (final Object item : values) {
			if (!(item instanceof Map<?, ?> itemMap)) {
				throw invalidJson(source);
			}
			final Map<String, Object> row = new LinkedHashMap<>();
			for (final Map.Entry<?, ?> entry : itemMap.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw invalidJson(source);
				}
				row.put(key, entry.getValue());
			}
			result.add(row);
		}
		return result;
	}

	private static IllegalArgumentException invalidJson(final String source) {
		return new IllegalArgumentException("JSON data must be an array of objects. source=" + source);
	}
}
