package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;

class GenerateDataValidationTest extends AbstractGeneratorCommandTest {
	@Test
	void rejectsInvalidSelectedConfigurationBeforeExecutingGeneration() throws Exception {
		try (var ds = newInternalDataSource()) {
			executeSql(ds, "CREATE TABLE GENERATOR_VALIDATION (ID INTEGER PRIMARY KEY)");
			try {
				var config = new TableGeneratorConfig();
				config.setName("GENERATOR_VALIDATION");
				var column = new ColumnGeneratorConfig();
				column.setName("UNKNOWN");
				config.addColumn(column);
				var calls = new AtomicInteger();
				var command = new GenerateDataInsertCommand() {
					@Override
					protected void applyFromFileByRow(java.sql.Connection connection,
							com.sqlapp.data.db.dialect.Dialect dialect, com.sqlapp.data.schemas.Table table,
							TableGeneratorConfig tableConfig) {
						calls.incrementAndGet();
					}
				};
				command.setDataSource(ds);
				command.setCloseDataSource(false);
				command.setIncludeTables("GENERATOR_VALIDATION");
				command.setTableConfigs(Map.of(config.getName(), List.of(config)));
				assertThrows(IllegalArgumentException.class, command::run);
				assertEquals(0, calls.get());
			} finally {
				dropTables(ds, "GENERATOR_VALIDATION");
			}
		}
	}
}
