package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

class GeneratorStaticFunctionTest extends AbstractGeneratorCommandTest {
	public static final class Functions {
		public static int initialId() { return 10; }
		public static int advance(int value) { return value + 1; }
		public static String advance(String value) { return value + "!"; }
		public static String decorate(String value, long index) { return value + "-" + index; }
		public static List<Map<String, Object>> sourceRows(int size) {
			var rows = new ArrayList<Map<String, Object>>();
			for (int i = 0; i < size; i++) rows.add(Map.of("SOURCE", i));
			return rows;
		}
		public static Map<String, Object> mapping(int source) { return Map.of("MAPPED", "source-" + source); }
		public static Object failingFunction() { throw new IllegalArgumentException("custom function failed"); }
	}

	private static class Command extends GenerateDataInsertCommand {
		@Override protected void info(Object... values) { }
		@Override protected void info(Object value) { }
		void generate(Connection connection, TableGeneratorConfig config) throws Exception {
			config.check();
			var table = new Table("FUNCTION_OUTPUT").setSchemaName("PUBLIC");
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			for (String name : List.of("LABEL", "MAPPED")) {
				table.getColumns().add(name, c -> c.setDataType(DataType.VARCHAR).setLength(100));
			}
			applyFromFileByRow(connection, DialectResolver.getInstance().getDialect(connection), table, config);
		}
	}

	@Test
	void importedJavaFunctionsGeneratePreviewAndInsertedValues() throws Exception {
		for (int size : new int[] {1, 3}) {
			for (boolean preview : new boolean[] {true, false}) {
				for (boolean countFirst : new boolean[] {true, false}) {
					var expected = new ArrayList<Map<String, Object>>();
					for (int i = 0; i < size; i++) {
						expected.add(Map.of("ID", 10 + i, "LABEL", "row-" + i, "MAPPED", "source-" + i));
					}
					assertEquals(expected, run(preview, countFirst, config(size)));
				}
			}
		}
	}

	@Test
	void javaFunctionFailureRetainsColumnExpressionAndRowDiagnostics() throws Exception {
		for (boolean preview : new boolean[] {true, false}) {
			for (boolean countFirst : new boolean[] {true, false}) {
				var config = config(3);
				config.getColumns().get("LABEL").setFormatExpression("failingFunction()");
				var failure = assertThrows(IllegalStateException.class, () -> run(preview, countFirst, config));
				assertTrue(failure.getMessage().contains("table=PUBLIC.FUNCTION_OUTPUT"));
				assertTrue(failure.getMessage().contains("startRowIndex=0"));
				assertTrue(failure.getMessage().contains("iterateIndex=0"));
				String chain = "";
				for (Throwable cause = failure; cause != null; cause = cause.getCause()) chain += cause.getMessage() + "\n";
				assertTrue(chain.contains("LABEL"));
				assertTrue(chain.contains("failingFunction()"));
				assertTrue(chain.contains("custom function failed"));
			}
		}
	}

	private TableGeneratorConfig config(int size) {
		var config = new TableGeneratorConfig();
		config.setName("FUNCTION_OUTPUT"); config.setSchemaName("PUBLIC");
		config.setStartValueSql("VALUES (0)");
		config.setDataSourceExpression("sourceRows(" + size + ")");
		config.setColumnMappingExpression("mapping(SOURCE)");
		var id = new ColumnGeneratorConfig();
		id.setName("ID"); id.setMinValue("initialId()"); id.setNextValue("advance(_previous.ID)");
		config.addColumn(id);
		var label = new ColumnGeneratorConfig();
		label.setName("LABEL"); label.setNextValue("advance('row')");
		label.setFormatExpression("decorate('row', _index)"); config.addColumn(label);
		return config;
	}

	private List<Map<String, Object>> run(boolean preview, boolean countFirst, TableGeneratorConfig config) throws Exception {
		url = "jdbc:hsqldb:mem:static_functions_" + java.util.UUID.randomUUID() + ";shutdown=true";
		try (var ds = newInternalDataSource(); var connection = ds.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE FUNCTION_OUTPUT (ID INTEGER, LABEL VARCHAR(100), MAPPED VARCHAR(100))");
			}
			var evaluator = new CachedMvelEvaluator();
			evaluator.addAllStaticMethodsImport(Functions.class.getName());
			var command = new Command(); command.setEvaluator(evaluator);
			command.setPreview(preview); command.setCountRowsBeforeInsert(countFirst); command.setDmlBatchSize(2);
			command.generate(connection, config);
			var rows = new ArrayList<Map<String, Object>>();
			try (var statement = connection.createStatement();
					var results = statement.executeQuery("SELECT ID, LABEL, MAPPED FROM FUNCTION_OUTPUT ORDER BY ID")) {
				while (results.next()) rows.add(Map.of("ID", results.getInt(1), "LABEL", results.getString(2), "MAPPED", results.getString(3)));
			}
			if (preview) {
				assertTrue(rows.isEmpty());
				return command.getPreviewRows().stream().map(GeneratorPreviewRow::values).toList();
			}
			return rows;
		}
	}
}
