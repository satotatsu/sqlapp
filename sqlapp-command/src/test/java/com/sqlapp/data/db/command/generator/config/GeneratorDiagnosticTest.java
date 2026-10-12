package com.sqlapp.data.db.command.generator.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.ExpressionExecutionException;

class GeneratorDiagnosticTest {
	@Test
	void nextValueFailureIncludesColumnExpressionAndPosition() {
		var config = new TableGeneratorConfig();
		config.setName("OUTPUT");
		config.setSchemaName("PUBLIC");
		var column = new ColumnGeneratorConfig();
		column.setName("ID");
		column.setNextValue("missingDiagnosticFunction()");
		config.addColumn(column);
		var failure = assertThrows(ExpressionExecutionException.class, () -> config.generateValue(4, 9));
		assertTrue(failure.getMessage().contains("column=ID"));
		assertTrue(failure.getMessage().contains("rowNumberArgument=4"));
		assertTrue(failure.getMessage().contains("generationIndex=9"));
		assertTrue(failure.getMessage().contains("missingDiagnosticFunction()"));
		assertNotNull(failure.getCause());
	}

	@Test
	void formatFailureIncludesColumnAndExpression() {
		var config = new TableGeneratorConfig();
		var column = new ColumnGeneratorConfig();
		column.setName("ID");
		column.setFormatExpression("missingDiagnosticFunction()");
		config.addColumn(column);
		var failure = assertThrows(ExpressionExecutionException.class, () -> config.generateValue(4, 9));
		assertTrue(failure.getMessage().contains("column=ID"));
		assertTrue(failure.getMessage().contains("phase=formatExpression"));
		assertTrue(failure.getMessage().contains("missingDiagnosticFunction()"));
		assertNotNull(failure.getCause());
	}

	@Test
	void mappingFailurePreservesCauseAndExpression() {
		var config = new TableGeneratorConfig();
		config.setName("OUTPUT");
		config.setColumnMappingExpression("missingDiagnosticFunction()");
		var failure = assertThrows(ExpressionExecutionException.class,
				() -> config.convertColumnMapping(Map.of()));
		assertTrue(failure.getMessage().contains("phase=columnMappingExpression"));
		assertTrue(failure.getMessage().contains("missingDiagnosticFunction()"));
		assertNotNull(failure.getCause());
	}
}
