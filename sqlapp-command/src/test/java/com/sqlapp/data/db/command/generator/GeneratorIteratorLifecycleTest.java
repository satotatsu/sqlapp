package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

class GeneratorIteratorLifecycleTest extends AbstractGeneratorCommandTest {
	private enum Mode { COUNT, PREVIEW, SINGLE, GENERAL }
	private enum Failure { NONE, HAS_NEXT, NEXT, GENERATE }

	private static class Source implements Iterable<Map<String, Object>> {
		final List<Cursor> cursors = new ArrayList<>();
		final RuntimeException original = new IllegalArgumentException("source or generation failure");
		final IOException closeFailure = new IOException("close failure");
		final int size;
		final Failure failure;
		final boolean failClose;
		Source(int size, Failure failure, boolean failClose) {
			this.size = size; this.failure = failure; this.failClose = failClose;
		}
		@Override public Iterator<Map<String, Object>> iterator() {
			var cursor = new Cursor(); cursors.add(cursor); return cursor;
		}
		private class Cursor implements Iterator<Map<String, Object>>, AutoCloseable {
			int position;
			int closes;
			@Override public boolean hasNext() {
				if (failure == Failure.HAS_NEXT && position == (size == 1 ? 0 : 1)) throw original;
				return position < size;
			}
			@Override public Map<String, Object> next() {
				if (failure == Failure.NEXT && position == (size == 1 ? 0 : 1)) throw original;
				position++; return Map.of();
			}
			@Override public void close() throws IOException {
				closes++;
				if (failClose) throw closeFailure;
			}
		}
	}

	private static class Command extends GenerateDataInsertCommand {
		@Override protected void info(Object... values) { }
		@Override protected void info(Object value) { }
		void generate(Connection connection, TableGeneratorConfig config) throws Exception {
			config.check();
			var table = new Table("LIFECYCLE_OUTPUT").setSchemaName("PUBLIC");
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			applyFromFileByRow(connection, DialectResolver.getInstance().getDialect(connection), table, config);
		}
	}

	@Test
	void closesEveryOpenedIteratorOnSuccessAndPreviewLimit() throws Exception {
		for (Mode mode : Mode.values()) {
			var source = new Source(mode == Mode.SINGLE ? 1 : 3, Failure.NONE, false);
			execute(mode, source);
			assertClosedOnce(source);
			if (mode == Mode.PREVIEW) assertEquals(1, source.cursors.get(0).position);
		}
	}

	@Test
	void preservesPrimaryFailureAndSuppressesCloseFailureInEveryPath() throws Exception {
		for (Mode mode : Mode.values()) {
			for (Failure failure : List.of(Failure.HAS_NEXT, Failure.NEXT, Failure.GENERATE)) {
				for (boolean failClose : new boolean[] {false, true}) {
					var source = new Source(mode == Mode.SINGLE ? 1 : 3, failure, failClose);
					var thrown = assertThrows(Exception.class, () -> execute(mode, source), mode + "/" + failure);
					assertTrue(contains(thrown, source.original), mode + "/" + failure);
					assertClosedOnce(source);
					if (failClose) assertTrue(contains(thrown, source.closeFailure), mode + "/" + failure);
				}
			}
		}
	}

	@Test
	void propagatesCloseFailureWithoutAnEarlierFailure() throws Exception {
		for (Mode mode : Mode.values()) {
			var source = new Source(mode == Mode.SINGLE ? 1 : 3, Failure.NONE, true);
			var thrown = assertThrows(Exception.class, () -> execute(mode, source), mode.toString());
			assertTrue(contains(thrown, source.closeFailure));
			assertClosedOnce(source);
		}
	}

	private void execute(Mode mode, Source source) throws Exception {
		var evaluator = new CachedMvelEvaluator() {
			@Override @SuppressWarnings("unchecked")
			public <T> T eval(String expression, Object context) {
				if (expression.equals("testMapping")) {
					if (source.failure == Failure.GENERATE) throw source.original;
					return (T) Map.of();
				}
				return super.eval(expression, context);
			}
			@Override @SuppressWarnings("unchecked")
			public <T> T eval(String expression, Map<?, ?> context) {
				if (expression.equals("testSource")) return (T) source;
				if (expression.equals("testMapping")) {
					if (source.failure == Failure.GENERATE) throw source.original;
					return (T) Map.of();
				}
				return super.eval(expression, context);
			}
		};
		var config = new TableGeneratorConfig() {
			@Override public long iterateCount() {
				// Count cleanup is exercised separately; reach the single-row INSERT path even on failure.
				return mode == Mode.SINGLE ? 1 : super.iterateCount();
			}
			@Override public Map<String, Object> generateValue(long row, long index) {
				if (source.failure == Failure.GENERATE) throw source.original;
				return super.generateValue(row, index);
			}
		};
		config.setName("LIFECYCLE_OUTPUT"); config.setSchemaName("PUBLIC");
		config.setStartValueSql("VALUES (0)");
		config.setDataSourceExpression("testSource");
		config.setColumnMappingExpression("testMapping");
		config.setEvaluator(evaluator);
		var column = new ColumnGeneratorConfig();
		column.setName("ID"); column.setMinValue("1"); column.setNextValue("_previous.ID + 1");
		config.addColumn(column);
		if (mode == Mode.COUNT) { config.iterateCount(); return; }
		url = "jdbc:hsqldb:mem:iterator_lifecycle_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE LIFECYCLE_OUTPUT (ID INTEGER)");
			}
			var command = new Command(); command.setEvaluator(evaluator);
			command.setPreview(mode == Mode.PREVIEW);
			command.setPreviewLimit(source.failure == Failure.NONE ? 1 : 10);
			command.setCountRowsBeforeInsert(mode == Mode.SINGLE);
			command.setDmlBatchSize(2);
			command.generate(connection, config);
		}
	}

	private void assertClosedOnce(Source source) {
		assertFalse(source.cursors.isEmpty());
		for (var cursor : source.cursors) assertEquals(1, cursor.closes);
	}

	private boolean contains(Throwable failure, Throwable expected) {
		if (failure == expected) return true;
		if (failure.getCause() != null && contains(failure.getCause(), expected)) return true;
		for (Throwable suppressed : failure.getSuppressed()) if (contains(suppressed, expected)) return true;
		return false;
	}
}
