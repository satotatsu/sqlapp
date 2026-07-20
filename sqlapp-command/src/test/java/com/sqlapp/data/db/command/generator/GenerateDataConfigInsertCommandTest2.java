/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand.RowMonitor;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.zaxxer.hikari.HikariDataSource;

class GenerateDataConfigInsertCommandTest2 extends AbstractGeneratorCommandTest {

//	@Test
	void test1() {
		test(command -> {
			command.setGeneratorConfigConsumer(config -> {
				config.setDataSourceExpression("iterator(10)");
			});
			command.setRowMonitor(createRowMonitor());
		}, command -> {

		});
	}

	@Test
	void testGenerationInsertBatch2() {
		final int dmlBatchSize = 2;
		final int iterate = 110;
		int[] handleCount = new int[1];
		test(command -> {
			command.setQueryCommitInterval(3);
			command.setDmlBatchSize(dmlBatchSize);
			command.setGeneratorConfigConsumer(config -> {
				config.setDataSourceExpression("iterator(" + iterate + ")");
				config.setStartValueSql(config.getStartValueSql() + "\nUNION ALL\n" + config.getStartValueSql());
				config.setInsertSql(config.getInsertSql() + "\n;\n" + config.getInsertSql());
			});
			long[] exptectedUpdatedRowCount = new long[1];
			RowMonitor rowMonitor = new RowMonitor(createRowMonitor()) {
				@Override
				public void handle(final TableGeneratorConfig tableConfig, final Map<String, Object> resultSetValueMap,
						long readRowCount, long dataSourceRowNumber, long generatedCount, long updatedRowCount,
						Map<String, Object> covertedColumnMapping, Map<String, Object> generatedValue,
						final Table startTable, final Table table, final List<SqlNode> insertSqlNodes) {
					this.internal.handle(tableConfig, resultSetValueMap, readRowCount, dataSourceRowNumber,
							generatedCount, updatedRowCount, covertedColumnMapping, generatedValue, startTable, table,
							insertSqlNodes);
					if (handleCount[0] % dmlBatchSize == 0) {
						exptectedUpdatedRowCount[0] = generatedCount * dmlBatchSize;
					}
					// readRowCount=0, dataSourceRowNumber=109, generatedCount=109,
					// updatedRowCount=216, insertSqlNodes.size=2
					// readRowCount=1, dataSourceRowNumber=0, generatedCount=110,
					// updatedRowCount=220, insertSqlNodes.size=2
					// assertEquals(dataSourceRowNumber + (readRowCount * iterate), generatedCount);
					assertEquals(exptectedUpdatedRowCount[0], updatedRowCount);
					handleCount[0]++;
				}
			};
			command.setRowMonitor(rowMonitor);
		}, command -> {
			assertEquals(220, handleCount[0]);
		});
	}

	@Test
	void testGenerationInsertBatch500() {
		final int dmlBatchSize = 500;
		final int iterate = 110;
		int[] handleCount = new int[1];
		test(command -> {
			command.setGeneratorConfigConsumer(config -> {
				command.setDmlBatchSize(dmlBatchSize);
				config.setDataSourceExpression("iterator(" + iterate + ")");
				config.setStartValueSql(config.getStartValueSql() + "\nUNION ALL\n" + config.getStartValueSql());
				config.setInsertSql(config.getInsertSql() + "\n;\n" + config.getInsertSql());
			});
			long[] exptectedUpdatedRowCount = new long[1];
			RowMonitor rowMonitor = new RowMonitor(createRowMonitor()) {
				@Override
				public void handle(final TableGeneratorConfig tableConfig, final Map<String, Object> resultSetValueMap,
						long readRowCount, long dataSourceRowNumber, long generatedCount, long updatedRowCount,
						Map<String, Object> covertedColumnMapping, Map<String, Object> generatedValue,
						final Table startTable, final Table table, final List<SqlNode> insertSqlNodes) {
					this.internal.handle(tableConfig, resultSetValueMap, readRowCount, dataSourceRowNumber,
							generatedCount, updatedRowCount, covertedColumnMapping, generatedValue, startTable, table,
							insertSqlNodes);
					if (handleCount[0] % dmlBatchSize == 0) {
						exptectedUpdatedRowCount[0] = generatedCount * dmlBatchSize;
					}
					// assertEquals(dataSourceRowNumber + (readRowCount * iterate), generatedCount);
					handleCount[0]++;
				}
			};
			command.setRowMonitor(rowMonitor);
		}, command -> {
			assertEquals(220, handleCount[0]);
		});
	}

	@Test
	void testCopyMode() {
		final int dmlBatchSize = 2;
		final int iterate = 200;
		test(command -> {
			command.setGeneratorConfigConsumer(config -> {
				config.setDataSourceExpression("iterator(" + iterate + ")");
			});
			command.setAfter((com, files) -> {
				command.setDmlBatchSize(dmlBatchSize);
				com.setQueryCommitInterval(3);
				command.setDmlBatchSize(dmlBatchSize);
				com.getTableConfigs().clear();
				com.setRowMonitor(createRowMonitor());
				for (File file : files) {
					if (!com.getFileFilter().test(file)) {
						return;
					}
					final TableGeneratorConfig config = command.getGeneratorConfigFactory().fromFile(file);
					config.setStartValueSql("SELECT 1 AS DUMMY_COL FROM PRODUCTS");
					config.setDataSourceExpression("iterator(1)");
					com.addTableGeneratorConfig(config, com.getTableConfigs());
				}
				System.out.println("=====================================");
				System.out.println("=====================================");
				System.out.println("=====================================");
				com.run();
			});
		}, command -> {
		});
	}

	private RowMonitor createRowMonitor() {
		RowMonitor rowMonitor = new RowMonitor() {
			@Override
			public void handle(final TableGeneratorConfig tableConfig, final Map<String, Object> resultSetValueMap,
					long readRowCount, long dataSourceRowNumber, long generatedCount, long updatedRowCount,
					Map<String, Object> covertedColumnMapping, Map<String, Object> generatedValue,
					final Table startTable, final Table table, final List<SqlNode> insertSqlNodes) {
				System.out.println("=====================================");
				System.out.println("tableConfig.name=" + tableConfig.getName());
				System.out.println("resultSetValueMap=" + resultSetValueMap);
				System.out.println("readRowCount=" + readRowCount + ", dataSourceRowNumber=" + dataSourceRowNumber
						+ ", generatedCount=" + generatedCount + ", updatedRowCount=" + updatedRowCount
						+ ", insertSqlNodes.size=" + insertSqlNodes.size());
				System.out.println("covertedColumnMapping=" + covertedColumnMapping);
				Map<String, Object> map = CommonUtils.linkedMap(generatedValue);
				map.remove("_min");
				map.remove("_max");
				System.out.println("generatedValue=" + map);
			}
		};
		return rowMonitor;
	}

	private void test(Consumer<GenerateDataConfigAndInsertCommand> cons,
			Consumer<GenerateDataConfigAndInsertCommand> afterCons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigAndInsertCommand command = new GenerateDataConfigAndInsertCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS");
			command.setCloseDataSource(false);
			command.setCommitLogEnabled(true);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "PRODUCTS");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			cons.accept(command);
			command.run();
			afterCons.accept(command);
			dropTables(command, "PRODUCTS");
		} finally {
			ds.close();
		}
	}

}
