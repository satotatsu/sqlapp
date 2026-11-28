/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Table;

/**
 * Iterator contract of the row iterator base classes: hasNext() is idempotent,
 * next() works without hasNext(), the end throws NoSuchElementException, and
 * {@code count} counts consumed rows only.
 */
class RowIteratorContractTest {

	@TempDir
	Path directory;

	/** Cursor whose hasNextInternal advances the source, like ResultSet.next(). */
	static final class Cursor {
		final List<String> values;
		int position = -1;
		int advances;
		int closes;

		Cursor(final List<String> values) {
			this.values = values;
		}

		boolean advance() {
			advances++;
			position++;
			return position < values.size();
		}

		String current() {
			return values.get(position);
		}
	}

	enum Base {
		ROW(Base::row), TEXT(Base::text);

		final Function<Object[], Iterator<Row>> factory;

		Base(final Function<Object[], Iterator<Row>> factory) {
			this.factory = factory;
		}

		Iterator<Row> create(final RowCollection rows, final long index, final Cursor cursor) {
			return factory.apply(new Object[] { rows, index, cursor });
		}

		private static Iterator<Row> row(final Object[] args) {
			final Cursor cursor = (Cursor) args[2];
			return new AbstractRowIterator<String>((RowCollection) args[0], (Long) args[1], (r, c, v) -> v) {
				@Override
				protected void preInitialize() {
				}

				@Override
				protected void initializeColumn() {
				}

				@Override
				protected String read() {
					return cursor.current();
				}

				@Override
				protected boolean hasNextInternal() {
					return cursor.advance();
				}

				@Override
				protected void set(final String value, final Row row) {
					row.setDataSourceRowNumber(count + 1);
					put(row, table.getColumns().get("V"), value);
				}

				@Override
				protected void doClose() {
					cursor.closes++;
				}
			};
		}

		private static Iterator<Row> text(final Object[] args) {
			final Cursor cursor = (Cursor) args[2];
			return new AbstractTextRowListIterator<String>((RowCollection) args[0], (Long) args[1], (r, c, v) -> v) {
				@Override
				protected void preInitialize() {
				}

				@Override
				protected void initializeColumn() {
				}

				@Override
				protected String read() {
					return cursor.current();
				}

				@Override
				protected boolean hasNextInternal() {
					return cursor.advance();
				}

				@Override
				protected void set(final String value, final Row row) {
					row.setDataSourceRowNumber(count + 1);
					put(row, table.getColumns().get("V"), value);
				}

				@Override
				protected void doClose() {
					cursor.closes++;
				}
			};
		}
	}

	private static RowCollection rows() {
		final Table table = new Table("T");
		table.getColumns().add(new Column("V"));
		return table.getRows();
	}

	@ParameterizedTest
	@EnumSource(Base.class)
	void repeatedHasNextDoesNotSkipRows(final Base base) {
		final Cursor cursor = new Cursor(List.of("a", "b", "c"));
		final Iterator<Row> iterator = base.create(rows(), 0L, cursor);
		final List<String> values = new ArrayList<>();
		final List<Long> numbers = new ArrayList<>();
		while (iterator.hasNext() && iterator.hasNext() && iterator.hasNext()) {
			final Row row = iterator.next();
			values.add((String) row.get("V"));
			numbers.add(row.getDataSourceRowNumber());
		}
		assertEquals(List.of("a", "b", "c"), values);
		assertEquals(List.of(1L, 2L, 3L), numbers);
		// One advance per row plus the one that found the end.
		assertEquals(4, cursor.advances);
		assertFalse(iterator.hasNext());
		assertEquals(4, cursor.advances);
		assertEquals(1, cursor.closes);
	}

	@ParameterizedTest
	@EnumSource(Base.class)
	void nextWorksWithoutHasNextAndThrowsAtTheEnd(final Base base) {
		final Cursor cursor = new Cursor(List.of("a", "b"));
		final Iterator<Row> iterator = base.create(rows(), 0L, cursor);
		assertEquals("a", iterator.next().get("V"));
		final Row second = iterator.next();
		assertEquals("b", second.get("V"));
		assertEquals(2L, second.getDataSourceRowNumber());
		assertThrows(NoSuchElementException.class, iterator::next);
		assertThrows(NoSuchElementException.class, iterator::next);
		assertEquals(1, cursor.closes);
	}

	@ParameterizedTest
	@EnumSource(Base.class)
	void emptySourceClosesOnFirstCheck(final Base base) {
		final Cursor cursor = new Cursor(List.of());
		final Iterator<Row> iterator = base.create(rows(), 0L, cursor);
		assertFalse(iterator.hasNext());
		assertFalse(iterator.hasNext());
		assertThrows(NoSuchElementException.class, iterator::next);
		assertEquals(1, cursor.advances);
		assertEquals(1, cursor.closes);
	}

	@ParameterizedTest
	@EnumSource(Base.class)
	void skippedRowsAreNotCounted(final Base base) {
		final Cursor cursor = new Cursor(List.of("a", "b", "c"));
		final Iterator<Row> iterator = base.create(rows(), 1L, cursor);
		assertTrue(iterator.hasNext());
		assertTrue(iterator.hasNext());
		final Row row = iterator.next();
		assertEquals("b", row.get("V"));
		assertEquals(1L, row.getDataSourceRowNumber());
		assertEquals("c", iterator.next().get("V"));
		assertFalse(iterator.hasNext());
	}

	@Test
	void jsonLineRowNumbersStartAtOne() throws Exception {
		final File file = directory.resolve("rows.jsonl").toFile();
		Files.writeString(file.toPath(), "{\"V\":\"a\"}\n{\"V\":\"b\"}\n", StandardCharsets.UTF_8);
		final Table table = new Table("T");
		table.getColumns().add(new Column("V"));
		table.setRowIteratorHandler(new JsonLineRowIteratorHandler(file));
		final List<Long> numbers = new ArrayList<>();
		for (final Row row : table.getRows()) {
			numbers.add(row.getDataSourceRowNumber());
		}
		assertEquals(List.of(1L, 2L), numbers);
	}
}
