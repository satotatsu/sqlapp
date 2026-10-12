package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.FileGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;

class GeneratorPreviewTest extends AbstractGeneratorCommandTest {
	private static class Command extends GenerateDataInsertCommand {
		void sample(Connection connection, TableGeneratorConfig config) throws Exception {
			config.check();
			applyFromFileByRow(connection, DialectResolver.getInstance().getDialect(connection), table(), config);
		}
		@Override protected void info(Object... values) { }
	}

	private static Table table() {
		var table = new Table("PREVIEW_OUTPUT").setSchemaName("PUBLIC");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		for (String name : List.of("GENERATED", "LABEL", "FILE_LABEL", "NULLABLE")) {
			table.getColumns().add(name, c -> c.setDataType(DataType.VARCHAR).setLength(100));
		}
		return table;
	}

	private void initializeDb(Connection connection) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE PREVIEW_START (ID INTEGER)");
			statement.execute("INSERT INTO PREVIEW_START VALUES (10), (20)");
			statement.execute("CREATE TABLE PREVIEW_OUTPUT (ID INTEGER, GENERATED VARCHAR(100), LABEL VARCHAR(100), FILE_LABEL VARCHAR(100), NULLABLE VARCHAR(100))");
		}
	}

	private TableGeneratorConfig config(int size, AtomicInteger reads, AtomicInteger closes) {
		var config = new TableGeneratorConfig() {
			@Override public long iterateCount() { throw new AssertionError("preview must not pre-count"); }
			@Override public Iterable<Map<String, Object>> getDataSource() {
				class Source implements Iterator<Map<String, Object>>, AutoCloseable {
					int position;
					public boolean hasNext() { return position < size; }
					public Map<String, Object> next() { position++; reads.incrementAndGet(); return Map.of(); }
					public void close() { closes.incrementAndGet(); }
				}
				return () -> new Source();
			}
		};
		config.setName("PREVIEW_OUTPUT");
		config.setSchemaName("PUBLIC");
		config.setStartValueSql("SELECT ID FROM PREVIEW_START ORDER BY ID");
		config.setColumnMappingExpression("['ID': 700]");
		config.setInsertSql("INVALID INSERT MUST NOT BE PARSED");
		config.setInitializeSql("INVALID INITIALIZE");
		config.setFinalizeSql("INVALID FINALIZE");
		config.setStartCountSql("INVALID START COUNT");
		config.setFinishCountSql("INVALID FINISH COUNT");
		var id = new ColumnGeneratorConfig();
		id.setName("ID"); id.setMinValue("1"); id.setNextValue("_previous.ID + 1"); config.addColumn(id);
		var generated = new ColumnGeneratorConfig();
		generated.setName("GENERATED"); generated.setMinValue("5");
		generated.setNextValue("_previous.GENERATED + 1"); generated.setFormatExpression("'formatted-' + _value");
		config.addColumn(generated);
		var query = new QueryGeneratorConfig();
		query.setGenerationGroup("reference");
		query.setSelectSql("SELECT 'reference-value' AS LABEL FROM PREVIEW_START WHERE ID=10");
		config.addQueryDefinition(query);
		var label = new ColumnGeneratorConfig(); label.setName("LABEL"); label.setLookupGroup("reference"); config.addColumn(label);
		var file = new FileGeneratorConfig();
		file.setLookupGroup("fileReference"); file.setDataSourceExpression("[['FILE_LABEL': 'file-value']]");
		config.addFileDefinition(file);
		var fileLabel = new ColumnGeneratorConfig(); fileLabel.setName("FILE_LABEL"); fileLabel.setLookupGroup("fileReference"); config.addColumn(fileLabel);
		var nullable = new ColumnGeneratorConfig(); nullable.setName("NULLABLE"); nullable.setNextValue("null"); config.addColumn(nullable);
		return config;
	}

	private Command command() {
		var command = new Command();
		command.setPreview(true);
		command.setRowMonitor(new GenerateDataInsertCommand.RowMonitor() {
			@Override public void handle(TableGeneratorConfig config, Map<String, Object> source, long read,
					long iterate, long generated, long updated, Map<String, Object> mapping,
					Map<String, Object> values, Table startTable, Table table,
					List<com.sqlapp.jdbc.sql.node.SqlNode> nodes) {
				throw new AssertionError("preview must not call the insertion monitor");
			}
		});
		command.setCommitHandler(c -> { throw new AssertionError("preview must not commit"); });
		command.setLastCommitHandler(c -> { throw new AssertionError("preview must not commit"); });
		return command;
	}

	@Test
	void previewsBoundedMappedFormattedLookupValuesAndTypesWithoutWrites() throws Exception {
		url = "jdbc:hsqldb:mem:preview_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			initializeDb(connection);
			var reads = new AtomicInteger(); var closes = new AtomicInteger();
			var command = command(); command.setPreviewLimit(3);
			command.sample(connection, config(2, reads, closes));
			var samples = command.getPreviewRows();
			assertEquals(3, samples.size()); assertEquals(3, reads.get()); assertEquals(2, closes.get());
			assertEquals(List.of(0L, 0L, 1L), samples.stream().map(GeneratorPreviewRow::startRowIndex).toList());
			assertEquals(List.of(0L, 1L, 0L), samples.stream().map(GeneratorPreviewRow::iterateIndex).toList());
			assertEquals(700, samples.get(0).values().get("ID"));
			assertEquals("formatted-5", samples.get(0).values().get("GENERATED"));
			assertEquals("reference-value", samples.get(0).values().get("LABEL"));
			assertEquals("file-value", samples.get(0).values().get("FILE_LABEL"));
			assertTrue(samples.get(0).values().containsKey("NULLABLE")); assertNull(samples.get(0).values().get("NULLABLE"));
			assertEquals("java.lang.Integer", samples.get(0).valueTypes().get("ID"));
			assertNull(samples.get(0).valueTypes().get("NULLABLE"));
			assertFalse(samples.get(0).values().containsKey("_previous"));
			assertThrows(UnsupportedOperationException.class, () -> samples.clear());
			assertThrows(UnsupportedOperationException.class, () -> samples.get(0).values().put("ID", 4));
			try (var statement = connection.createStatement(); var results = statement.executeQuery("SELECT COUNT(*) FROM PREVIEW_OUTPUT")) {
				assertTrue(results.next()); assertEquals(0, results.getInt(1));
			}
		}
	}

	@Test
	void publicEntryPointResetsResultsAndSkipsTransactionCallbacks() throws Exception {
		url = "jdbc:hsqldb:mem:preview_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			initializeDb(connection);
			var command = command(); command.setDataSource(ds); command.setCloseDataSource(false);
			command.setIncludeSchemas("PUBLIC"); command.setIncludeTables("PREVIEW_OUTPUT");
			command.setTableConfigs(Map.of("PREVIEW_OUTPUT", List.of(config(100, new AtomicInteger(), new AtomicInteger()))));
			assertEquals(10, command.getPreviewLimit()); command.run();
			var first = command.getPreviewRows(); assertEquals(10, first.size());
			command.setTableConfigs(Map.of("PREVIEW_OUTPUT", List.of(config(0, new AtomicInteger(), new AtomicInteger()))));
			command.run(); assertTrue(command.getPreviewRows().isEmpty()); assertEquals(10, first.size());
		}
	}

	@Test
	void limitAppliesPerConfigurationAndEmptyStartsDoNotEvaluateSource() throws Exception {
		url = "jdbc:hsqldb:mem:preview_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			initializeDb(connection);
			var command = command(); command.setPreviewLimit(2);
			command.setDataSource(ds); command.setCloseDataSource(false);
			command.setIncludeSchemas("PUBLIC"); command.setIncludeTables("PREVIEW_OUTPUT");
			command.setTableConfigs(Map.of("PREVIEW_OUTPUT", List.of(
					config(Integer.MAX_VALUE, new AtomicInteger(), new AtomicInteger()),
					config(Integer.MAX_VALUE, new AtomicInteger(), new AtomicInteger()))));
			command.run(); assertEquals(4, command.getPreviewRows().size());
			var reads = new AtomicInteger(); var closes = new AtomicInteger();
			var emptyStart = config(100, reads, closes);
			emptyStart.setStartValueSql("SELECT ID FROM PREVIEW_START WHERE 1=0");
			command.setTableConfigs(Map.of("PREVIEW_OUTPUT", List.of(emptyStart)));
			command.run(); assertTrue(command.getPreviewRows().isEmpty());
			assertEquals(0, reads.get()); assertEquals(0, closes.get());
		}
	}

	@Test
	void invalidLimitFailsBeforeReadingSources() {
		var command = command(); command.setPreviewLimit(0);
		assertThrows(IllegalArgumentException.class, command::run);
	}

	@Test
	void expressionFailureKeepsPositionAndClosesIterator() throws Exception {
		url = "jdbc:hsqldb:mem:preview_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			initializeDb(connection);
			var closes = new AtomicInteger(); var config = config(2, new AtomicInteger(), closes);
			config.setColumnMappingExpression("missingPreviewFunction()");
			var failure = assertThrows(IllegalStateException.class, () -> command().sample(connection, config));
			assertTrue(failure.getMessage().contains("startRowIndex=0"));
			assertTrue(failure.getMessage().contains("iterateIndex=0"));
			assertTrue(failure.getMessage().contains("missingPreviewFunction()"));
			assertEquals(1, closes.get());
		}
	}
}
