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
import java.util.NoSuchElementException;

import com.sqlapp.util.JsonConverter;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;

/**
 * Reads a JSON array whose elements are row objects. Rows are decoded
 * incrementally; errors in later rows are reported during iteration. Close the
 * returned {@link AutoCloseable} iterator when stopping early.
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
		return new JsonIterator(converter.getObjectMapper().createParser(file), file.getAbsolutePath());
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final Path path) {
		return iterator(path.toFile());
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final InputStream inputStream) {
		return new JsonIterator(converter.getObjectMapper().createParser(inputStream), "input stream");
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(final Reader reader) {
		return new JsonIterator(converter.getObjectMapper().createParser(reader), "reader");
	}

	private final class JsonIterator implements Iterator<Map<String, Object>>, AutoCloseable {
		private final JsonParser parser;
		private final String source;
		private final ObjectReader rowReader = converter.getObjectMapper().readerFor(Object.class)
				.without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		private JsonToken token;
		private boolean closed;

		JsonIterator(JsonParser parser, String source) {
			this.parser = parser;
			this.source = source;
			try {
				if (parser.nextToken() != JsonToken.START_ARRAY) {
					throw invalidJson(source);
				}
				advance();
			} catch (RuntimeException | Error failure) {
				closeOnFailure(failure);
				throw failure;
			}
		}

		private void advance() {
			token = parser.nextToken();
			if (token == JsonToken.END_ARRAY) {
				if (parser.nextToken() != null) {
					throw invalidJson(source);
				}
				close();
			} else if (token == null) {
				throw invalidJson(source);
			}
		}

		@Override
		public boolean hasNext() {
			return !closed;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Map<String, Object> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			try {
				if (token != JsonToken.START_OBJECT) {
					throw invalidJson(source);
				}
				final Map<String, Object> row = (Map<String, Object>) rowReader.readValue(parser);
				advance();
				return row;
			} catch (RuntimeException | Error failure) {
				closeOnFailure(failure);
				throw failure;
			}
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				parser.close();
			}
		}

		private void closeOnFailure(Throwable failure) {
			try {
				close();
			} catch (RuntimeException | Error closeFailure) {
				if (failure != closeFailure) {
					failure.addSuppressed(closeFailure);
				}
			}
		}
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
