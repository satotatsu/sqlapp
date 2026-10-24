package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.datatype.DataType;

class GeneratorStreamingProgressTest extends AbstractGeneratorCommandTest {
	private static final String START_SQL = "SELECT ID FROM GENERATOR_START ORDER BY ID";

	private static class Command extends GenerateDataInsertCommand {
		final List<String> messages = new ArrayList<>();

		@Override
		protected void info(Object value) {
			messages.add(String.valueOf(value));
		}

		@Override
		protected void info(Object... values) {
			messages.add(java.util.Arrays.stream(values).map(String::valueOf)
					.collect(java.util.stream.Collectors.joining()));
		}

		void generate(Connection connection, Dialect dialect, Table table, TableGeneratorConfig config)
				throws Exception {
			applyFromFileByRow(connection, dialect, table, config);
		}
	}

	@Test
	void streamingSkipsBothCountPassesAndProducesSameRowsAcrossBatchSizes() throws Exception {
		for (int batch : new int[] { 1, 2, 10 }) {
			for (int size : new int[] { 0, 1, 3 }) {
				assertEquals(run(true, size, batch, 2), run(false, size, batch, 2));
			}
		}
	}

	@Test
	void expressionPositionsAreIndependentOfCountingAndBatchSize() throws Exception {
		for (int size : new int[] { 1, 3 }) {
			var expected = new ArrayList<Integer>();
			for (int start = 0; start < 5; start++) {
				for (int iterate = 0; iterate < size; iterate++) {
					expected.add(start * 1000 + start * size + iterate + 1);
				}
			}
			for (int batch : new int[] { 1, 2, 10 }) {
				for (boolean countFirst : new boolean[] { true, false }) {
					assertEquals(expected, run(countFirst, size, batch, 5, -3),
							"countFirst=" + countFirst + ", size=" + size + ", batch=" + batch);
				}
			}
		}
	}

	@Test
	void varyingIterateCountsPreservePositionsAcrossEmptyStartsAndBatches() throws Exception {
		for (var sizes : List.of(List.of(0, 3, 1, 0, 2, 0), List.of(3, 0, 1, 2, 0, 1))) {
			var expected = new ArrayList<Integer>();
			int generated = 0;
			for (int start = 0; start < sizes.size(); start++) {
				for (int iterate = 0; iterate < sizes.get(start); iterate++) {
					expected.add(start * 1000 + generated++ + 1);
				}
			}
			for (boolean countFirst : new boolean[] { true, false }) {
				for (int batch : new int[] { 1, 2, 10 }) {
					assertEquals(expected, run(countFirst, 3, batch, sizes.size(), -1, "null", sizes),
							"sizes=" + sizes + ", countFirst=" + countFirst + ", batch=" + batch);
				}
			}
		}
	}

	@Test
	void varyingIterateFailuresReportPositionsAfterEmptyStarts() throws Exception {
		var sizes = List.of(0, 3, 1, 0, 2, 0);
		for (boolean countFirst : new boolean[] { true, false }) {
			for (int batch : new int[] { 1, 2, 10 }) {
				for (int failIndex : new int[] { 2, 3, 5 }) {
					var failure = assertThrows(IllegalStateException.class,
							() -> run(countFirst, 3, batch, sizes.size(), failIndex, "null", sizes));
					int start = failIndex == 2 ? 1 : failIndex == 3 ? 2 : 4;
					int iterate = failIndex == 2 ? 2 : failIndex == 3 ? 0 : 1;
					assertTrue(failure.getMessage().contains("startRowIndex=" + start));
					assertTrue(failure.getMessage().contains("iterateIndex=" + iterate));
					assertTrue(failure.getMessage().contains("convertedInputCount=" + failIndex));
					assertEquals("test generation failure", failure.getCause().getMessage());
				}
			}
		}
	}

	@Test
	void nullMappingPreservesGeneratedValuesAcrossInsertionPaths() throws Exception {
		for (boolean countFirst : new boolean[] { true, false }) {
			for (int size : new int[] { 1, 3 }) {
				for (int batch : new int[] { 1, 2, 10 }) {
					assertEquals(run(countFirst, size, batch, 5), run(countFirst, size, batch, 5, -1, "null"));
				}
			}
		}
	}

	@Test
	void preCountStillRejectsNonMapMappingResults() {
		var config = new TableGeneratorConfig();
		config.setDataSourceExpression("iterator(1)");
		config.setColumnMappingExpression("123");
		assertThrows(com.sqlapp.data.db.command.exceptions.InvalidExpressionResultTypeException.class,
				config::iterateCount);
	}

	@Test
	void emptyStartQueryCompletesWithZeroActualRowsInBothModes() throws Exception {
		assertEquals(List.of(), run(true, 3, 2, 0));
		assertEquals(List.of(), run(false, 3, 2, 0));
	}

	@Test
	void failuresReportActualDeferredIteratePositionInBothModes() throws Exception {
		for (boolean countFirst : new boolean[] { true, false }) {
			for (int batch : new int[] { 1, 10 }) {
				var failure = assertThrows(IllegalStateException.class, () -> run(countFirst, 3, batch, 2, 2));
				assertTrue(failure.getMessage().contains("table=PUBLIC.GENERATOR_OUTPUT"));
				assertTrue(failure.getMessage().contains("startRowIndex=0"));
				assertTrue(failure.getMessage().contains("iterateIndex=2"));
				assertEquals("test generation failure", failure.getCause().getMessage());
			}
		}
		var failure = assertThrows(IllegalStateException.class, () -> run(true, 1, 10, 2, 1));
		assertTrue(failure.getMessage().contains("startRowIndex=1"));
		assertTrue(failure.getMessage().contains("iterateIndex=0"));
	}

	@Test
	void sqlFailuresRetainJdbcDetailsInBothModes() throws Exception {
		for (boolean countFirst : new boolean[] { true, false }) {
			var failure = assertThrows(java.sql.SQLException.class, () -> run(countFirst, 1, 10, 2, -2));
			assertTrue(failure.getMessage().contains("table=PUBLIC.GENERATOR_OUTPUT"));
			assertTrue(failure.getMessage().contains("convertedInputCount=2"));
			assertTrue(failure.getMessage().contains("Exact failing row within a JDBC batch is unknown"));
			var original = assertInstanceOf(java.sql.SQLException.class, failure.getCause());
			assertEquals(original.getSQLState(), failure.getSQLState());
			assertEquals(original.getErrorCode(), failure.getErrorCode());
			if (original instanceof java.sql.BatchUpdateException batch) {
				assertArrayEquals(batch.getLargeUpdateCounts(),
						assertInstanceOf(java.sql.BatchUpdateException.class, failure).getLargeUpdateCounts());
			}
		}
	}

	private List<Integer> run(boolean countFirst, int size, int batch, int startRows) throws Exception {
		return run(countFirst, size, batch, startRows, -1);
	}

	private List<Integer> run(boolean countFirst, int size, int batch, int startRows, int failIndex) throws Exception {
		return run(countFirst, size, batch, startRows, failIndex, "new java.util.HashMap()");
	}

	private List<Integer> run(boolean countFirst, int size, int batch, int startRows, int failIndex,
			String mappingExpression) throws Exception {
		return run(countFirst, size, batch, startRows, failIndex, mappingExpression, null);
	}

	private List<Integer> run(boolean countFirst, int size, int batch, int startRows, int failIndex,
			String mappingExpression, List<Integer> sizes) throws Exception {
		url = "jdbc:hsqldb:mem:generator_diagnostics_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE GENERATOR_START (ID INTEGER)");
				statement.execute("CREATE TABLE GENERATOR_OUTPUT (ID INTEGER PRIMARY KEY)");
				for (int i = 0; i < startRows; i++) {
					statement.execute("INSERT INTO GENERATOR_START VALUES (" + ((i + 1) * 10) + ")");
				}
			}
			var queries = new AtomicInteger();
			Connection observed = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						try {
							Object result = method.invoke(connection, args);
							if (method.getName().equals("prepareStatement") && START_SQL.equals(args[0])) {
								PreparedStatement statement = (PreparedStatement) result;
								return Proxy.newProxyInstance(getClass().getClassLoader(),
										new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
											if (m.getName().equals("executeQuery"))
												queries.incrementAndGet();
											try {
												return m.invoke(statement, a);
											} catch (InvocationTargetException e) {
												throw e.getCause();
											}
										});
							}
							return result;
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var counts = new AtomicInteger();
			var sources = new AtomicInteger();
			var config = new TableGeneratorConfig() {
				private int currentStart;

				@Override
				public void setSqlStartValue(Map<String, Object> values) {
					super.setSqlStartValue(values);
					currentStart = ((Number) values.get("ID")).intValue() / 10 - 1;
				}

				@Override
				public Map<String, Object> generateValue(long row, long index) {
					if (index == failIndex)
						throw new IllegalArgumentException("test generation failure");
					var values = super.generateValue(row, index);
					if (failIndex == -2)
						values.put("ID", 1);
					return values;
				}

				@Override
				public long iterateCount() {
					counts.incrementAndGet();
					return super.iterateCount();
				}

				@Override
				public Iterable<Map<String, Object>> getDataSource() {
					sources.incrementAndGet();
					if (sizes == null)
						return super.getDataSource();
					// Simulate a source whose Iterate length depends on the current StartValueSQL
					// row.
					return java.util.Collections.nCopies(sizes.get(currentStart), Map.of());
				}
			};
			config.setName("GENERATOR_OUTPUT");
			config.setSchemaName("PUBLIC");
			config.setStartValueSql(START_SQL);
			config.setDataSourceExpression("iterator(" + size + ")");
			config.setColumnMappingExpression(mappingExpression);
			var column = new ColumnGeneratorConfig();
			column.setName("ID");
			column.setMinValue("1");
			column.setNextValue("_previous.ID + 1");
			if (failIndex == -3 || sizes != null)
				column.setFormatExpression("_row_number * 1000 + _index + 1");
			config.addColumn(column);
			config.check();
			var table = new Table("GENERATOR_OUTPUT").setSchemaName("PUBLIC");
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			var command = new Command();
			assertTrue(command.isCountRowsBeforeInsert());
			command.setCountRowsBeforeInsert(countFirst);
			command.setDmlBatchSize(batch);
			command.setQueryCommitInterval(2);
			command.generate(observed, DialectResolver.getInstance().getDialect(connection), table, config);
			connection.commit();
			assertEquals(countFirst ? 1 : 0, counts.get());
			assertEquals(countFirst && size == 0 ? 1 : countFirst ? 2 : 1, queries.get());
			assertEquals(countFirst && size == 0 ? 0 : startRows, sources.get());
			int expected = sizes == null ? startRows * size : sizes.stream().mapToInt(Integer::intValue).sum();
			assertTrue(command.messages.stream()
					.anyMatch(m -> m.contains("Insert completed. numberOfRows=[" + expected + "]")));
			if (!countFirst)
				assertFalse(command.messages.stream().anyMatch(m -> m.contains("%")));
			if (!countFirst && expected > 0) {
				assertTrue(command.messages.stream()
						.anyMatch(m -> m.contains("total=[unknown]") && m.contains("rowsPerSecond=")));
			}
			var rows = new ArrayList<Integer>();
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT ID FROM GENERATOR_OUTPUT ORDER BY ID")) {
				while (result.next())
					rows.add(result.getInt(1));
			}
			assertEquals(expected, rows.size());
			return rows;
		}
	}
}
