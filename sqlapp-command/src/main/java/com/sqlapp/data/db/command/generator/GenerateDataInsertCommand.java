/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.command.OutputFormatType;
import com.sqlapp.data.db.command.generator.config.FileGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.generator.util.CachedMvelEvaluatorUtils;
import com.sqlapp.data.db.command.generator.util.GeneratorMvelUtils;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.DmlBatchSizeProperty;
import com.sqlapp.data.db.command.properties.FetchSizeProperty;
import com.sqlapp.data.db.command.properties.FileFilterProperty;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.ForeignKeyDefinitionDirectoryProperty;
import com.sqlapp.data.db.command.properties.GeneratorConfigFactoryProperty;
import com.sqlapp.data.db.command.properties.QueryCommitIntervalProperty;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableOrder;
import com.sqlapp.data.schemas.VirtualForeignKeyLoader;
import com.sqlapp.iterable.CombinedIterable;
import com.sqlapp.iterable.IndexedConvertIterable;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.function.SQLRunnable;
import com.sqlapp.jdbc.sql.CommitCountHolder;
import com.sqlapp.jdbc.sql.GeneratedKeyInfo;
import com.sqlapp.jdbc.sql.JdbcBatchIterateHander;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.JdbcHandlerUtils;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class GenerateDataInsertCommand extends AbstractTableCommand implements FilesProperty, DirectoryProperty,
		QueryCommitIntervalProperty, FileFilterProperty, UseSchemaNameDirectoryProperty, GeneratorConfigFactoryProperty,
		ForeignKeyDefinitionDirectoryProperty, FetchSizeProperty, DmlBatchSizeProperty {
	/** input files */
	private List<File> files;
	/** input file directory */
	private File directory;
	/** useSchemaNameDirectory */
	private boolean useSchemaNameDirectory = false;
	/** fetchSize */
	private int fetchSize = 10000;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;
	/** query commit interval */
	private long queryCommitInterval = Long.MAX_VALUE;
	/** 式評価 */
	private CachedMvelEvaluator evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();
	/** TableDataGeneratorConfigFactory */
	private TableGeneratorConfigFactory generatorConfigFactory = new TableGeneratorConfigFactory();
	/** Virtual foreign Key definitions */
	private File foreignKeyDefinitionDirectory = null;
	private RowMonitor rowMonitor = new RowMonitor();

	private Map<String, List<TableGeneratorConfig>> tableConfigs;

	public GenerateDataInsertCommand() {
		this.setDmlBatchSize(500);
	}

	private VirtualForeignKeyLoader createVirtualForeignKeyLoader() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		return loader;
	}

	@Override
	protected void doRun() {
		if (this.evaluator == null) {
			this.evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();
		} else {
			this.evaluator.addAllStaticMethodsImport(GeneratorMvelUtils.class);
		}
		final Map<String, List<TableGeneratorConfig>> tableConfigs;
		if (this.tableConfigs != null) {
			tableConfigs = this.tableConfigs;
		} else {
			try {
				tableConfigs = readConfig();
			} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
				throw new RuntimeException(e);
			}
			if (tableConfigs.isEmpty()) {
				if (directory != null) {
					info("File not found. configDirectory=" + directory.getAbsolutePath());
				}
				return;
			}
		}
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final SchemaReader schemaReader = getSchemaReader(connection, dialect);
			final Set<String> schemaNames = CommonUtils.lowerSet();
			if (isUseSchemaNameDirectory()) {
				final File[] directories = getDirectory().listFiles(c -> c.isDirectory());
				if (directories != null) {
					for (final File directory : directories) {
						final String name = directory.getName();
						schemaNames.add(name);
					}
				}
			}
			final Map<String, Schema> schemaMap;
			if (isUseSchemaNameDirectory()) {
				schemaMap = this.getSchemas(connection, dialect, schemaReader,
						(s) -> schemaNames.contains(s.getName()));
			} else {
				schemaMap = this.getSchemas(connection, dialect, schemaReader, s -> true);
			}
			final Catalog catalog = new Catalog();
			catalog.setDialect(dialect);
			final List<Table> tables = CommonUtils.list();
			schemaMap.forEach((k, v) -> {
				catalog.getSchemas().add(v);
				v.getTables().forEach(t -> {
					tables.add(t);
				});
			});
			VirtualForeignKeyLoader loader = createVirtualForeignKeyLoader();
			loader.load(catalog, getForeignKeyDefinitionDirectory());
			final List<Table> sorted = SchemaUtils.getNewSortedTableList(tables, TableOrder.CREATE);
			if (tables.isEmpty()) {
				throw new TableNotFoundException("includeSchemas=" + Arrays.toString(this.getIncludeSchemas())
						+ ", excludeSchemas=" + Arrays.toString(this.getExcludeSchemas()) + ", includeTables="
						+ Arrays.toString(this.getIncludeTables()) + ", excludeTables="
						+ Arrays.toString(this.getExcludeTables()));
			}
			connection.setAutoCommit(false);
			for (final Table table : sorted) {
				final List<TableGeneratorConfig> tableConfigList = tableConfigs.get(table.getName());
				if (tableConfigList == null) {
					continue;
				}
				for (TableGeneratorConfig tableConfig : tableConfigList) {
					if (isUseSchemaNameDirectory()) {
						if (tableConfig.getParentDirectory() == null) {
							continue;
						}
						if (!CommonUtils.eqIgnoreCase(table.getSchemaName(),
								tableConfig.getParentDirectory().getName())) {
							continue;
						}
					}
					info("");
					applyFromFileByRow(connection, dialect, table, tableConfig);
					tableConfig.clear();
					tableConfigs.remove(table.getName());
				}
			}
		});
	}

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final TableGeneratorConfig tableConfig) throws SQLException {
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		final long rowAmplificationFactor = tableConfig.iterateCount();
		info(LOG_SEPARATOR_START, table.getName(), " Insert start. batchSize=[", batchSize, "]. start=[",
				startLocalTime, "].", LOG_SEPARATOR_END);
		if (!CommonUtils.isBlank(tableConfig.getStartCountSql())) {
			executeSql(connection, dialect, table, "Start Select count", tableConfig.getStartCountSql());
		}
		info(MESSAGE_SEPARATOR_START, "rowAmplificationFactor=[" + rowAmplificationFactor + "]", MESSAGE_SEPARATOR_END);
		tableConfig.initializeTableColumnData(table);
		Map<String, QueryGeneratorConfig> cacheMap = CommonUtils.map();
		tableConfig.loadData(connection, (index, config) -> {
			execute(config.getGenerationGroup() + " SQL", () -> {
				info(config.getSelectSql());
				String key = config.getConditionKey();
				final QueryGeneratorConfig cache = cacheMap.get(key);
				if (cache != null) {
					config.copyData(cache);
					info(config.getValues().size() + " rows cache used.");
				} else {
					config.loadData(connection);
					cacheMap.put(key, config);
					info(config.getValues().size() + " rows selected.");
				}
			});
		});
		Map<String, FileGeneratorConfig> fileCacheMap = CommonUtils.map();
		tableConfig.loadFileData((index, config) -> {
			execute(config.getLookupGroup() + " File", () -> {
				info(config.getDataSourceExpression());
				String key = config.getConditionKey();
				FileGeneratorConfig cache = fileCacheMap.get(key);
				if (cache != null) {
					config.copyData(cache);
					info(config.getValues().size() + " rows cache used.");
				} else {
					config.loadData();
					fileCacheMap.put(key, config);
					info(config.getValues().size() + " rows selected.");
				}
			});
		});
		tableConfig.setEvaluator(evaluator);
		tableConfig.calculateInitialObjectValues();
		final SqlConverter sqlConverter = getSqlConverter();
		final List<SqlNode> insertSqlNodes;
		final String insertSql;
		if (CommonUtils.isBlank(tableConfig.getInsertSql())) {
			insertSql = this.getGeneratorConfigFactory().createInsertSql(table, dialect, this.getTableOptions(),
					SqlType.INSERT);
			insertSqlNodes = createSqlNode(dialect, sqlConverter, insertSql);
		} else {
			insertSql = tableConfig.getInsertSql().trim();
			insertSqlNodes = createSqlNode(dialect, sqlConverter, insertSql);
		}
		final ParametersContext contextForStartValue = new ParametersContext();
		final SqlNode startValueSqlNode = sqlConverter.parseSql(dialect, contextForStartValue,
				tableConfig.getStartValueSql());
		final Table startTable = new Table();
		long[] startValueCounter = new long[1];
		final SqlParameterCollection sqlParameterCollection = startValueSqlNode.eval(contextForStartValue);
		sqlParameterCollection.setFetchSize(fetchSize);
		execute(table.getName() + " Start Value SQL", () -> {
			info(tableConfig.getStartValueSql());
			try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection,
					sqlParameterCollection)) {
				dialect.setFetchSizeForStream(statement, fetchSize);
				// 最初にStartQueryの対象行数だけ調べる
				try (final ResultSet resultSet = statement.executeQuery()) {
					startTable.readMetaData(resultSet);
					while (resultSet.next()) {
						startValueCounter[0]++;
					}
				}
			}
		});
		if (!CommonUtils.isBlank(tableConfig.getInitializeSql())) {
			executeSql(connection, dialect, table, "Initialize SQL", tableConfig.getInitializeSql());
		}
		final long total = rowAmplificationFactor * startValueCounter[0];
		execute(table.getName() + " Insert SQL", () -> {
			try {
				connection.setAutoCommit(false);
				info(insertSql);
				if (rowAmplificationFactor > 1) {
					insertAll(connection, dialect, sqlParameterCollection, startTable, table, rowAmplificationFactor,
							insertSqlNodes, total, tableConfig);
				} else {
					copyInsertAll(connection, dialect, sqlParameterCollection, startTable, table,
							rowAmplificationFactor, insertSqlNodes, total, tableConfig);
				}
			} catch (SQLException e) {
				throw e;
			} finally {
				table.setRowIteratorHandler(null);
			}
		});
		if (!CommonUtils.isBlank(tableConfig.getFinalizeSql())) {
			executeSql(connection, dialect, table, "Finalize SQL", tableConfig.getFinalizeSql());
			commit(connection);
		}
		if (!CommonUtils.isBlank(tableConfig.getFinishCountSql())) {
			executeSql(connection, dialect, table, "Select count", tableConfig.getFinishCountSql());
		}
		final long end = System.currentTimeMillis();
		final LocalDateTime endLocalTime = LocalDateTime.now();
		info(LOG_SEPARATOR_START, table.getName(), " Insert completed. numberOfRows=[", total, "]. start=[",
				startLocalTime, "]. end=[", endLocalTime, "]. [", (end - start), " ms].", LOG_SEPARATOR_END);
	}

	private void insertAll(Connection connection, final Dialect dialect,
			final SqlParameterCollection sqlParameterCollection, final Table startTable, final Table table,
			final long rowAmplificationFactor, final List<SqlNode> insertSqlNodes, final long totalRows,
			final TableGeneratorConfig tableConfig) throws SQLException {
		int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, sqlParameterCollection)) {
			dialect.setFetchSizeForStream(statement, fetchSize);
			final long[] readRowCount = new long[1];
			final long[] generatedCount = new long[1];
			final long[] updatedRowCount = new long[1];
			List<Map<String, Object>> resultSetValueMapList = CommonUtils.list();
			final JdbcBatchIterateHander handler = createJdbcBatchIterateHander(connection, dialect, table,
					insertSqlNodes, totalRows, generatedCount, updatedRowCount, o -> {
						@SuppressWarnings("unchecked")
						final Map<String, Object> vals = (Map<String, Object>) o;
						final ParametersContext context = new ParametersContext(table, vals);
						context.putAll(this.getContext());
						generatedCount[0]++;
						return context;
					}, conn -> {
						commit(conn);
					});
			try (final ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					final Map<String, Object> resultSetValueMap = CommonUtils.upperMap();
					for (int j = 0; j < startTable.getColumns().size(); j++) {
						final Object obj = resultSet.getObject(j + 1);
						final Column column = startTable.getColumns().get(j);
						resultSetValueMap.put(column.getName(), obj);
					}
					resultSetValueMapList.add(resultSetValueMap);
					if (resultSetValueMapList.size() > batchSize) {
						List<Iterable<Map<String, Object>>> iterableList = CommonUtils.list();
						for (int j = 0; j < resultSetValueMapList.size(); j++) {
							final Map<String, Object> currentResultSetValueMap = resultSetValueMapList.get(j);
							final Iterable<Map<String, Object>> dataSourceIterable = tableConfig.getDataSource();
							final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> countConvertIterable = new IndexedConvertIterable<>(
									itr -> {
										tableConfig.setSqlStartValue(currentResultSetValueMap);
									}, dataSourceIterable, (i, map) -> {
										final Map<String, Object> covertedColumnMapping = tableConfig
												.convertColumnMapping(map);
										final Map<String, Object> generatedValue = tableConfig
												.generateValue(readRowCount[0], generatedCount[0]);
										getRowMonitor().handle(tableConfig, currentResultSetValueMap, readRowCount[0],
												i, generatedCount[0], updatedRowCount[0], covertedColumnMapping,
												generatedValue, startTable, table, insertSqlNodes);
										generatedValue.putAll(covertedColumnMapping);
										return generatedValue;
									});
							iterableList.add(countConvertIterable);
						}
						final CombinedIterable<Map<String, Object>> combinedIterable = new CombinedIterable<Map<String, Object>>(
								iterableList);
						handler.execute(connection, combinedIterable);
						resultSetValueMapList.clear();
					}
					readRowCount[0]++;
				}
			}
			if (!CommonUtils.isEmpty(resultSetValueMapList)) {
				List<Iterable<Map<String, Object>>> iterableList = CommonUtils.list();
				for (int j = 0; j < resultSetValueMapList.size(); j++) {
					final Map<String, Object> currentResultSetValueMap = resultSetValueMapList.get(j);
					tableConfig.setSqlStartValue(currentResultSetValueMap);
					final Iterable<Map<String, Object>> dataSourceIterable = tableConfig.getDataSource();
					final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> countConvertIterable = new IndexedConvertIterable<>(
							itr -> {
								tableConfig.setSqlStartValue(currentResultSetValueMap);
							}, dataSourceIterable, (i, map) -> {
								final Map<String, Object> covertedColumnMapping = tableConfig.convertColumnMapping(map);
								final Map<String, Object> generatedValue = tableConfig.generateValue(readRowCount[0],
										generatedCount[0]);
								getRowMonitor().handle(tableConfig, currentResultSetValueMap, readRowCount[0], i,
										generatedCount[0], updatedRowCount[0], covertedColumnMapping, generatedValue,
										startTable, table, insertSqlNodes);
								generatedValue.putAll(covertedColumnMapping);
								return generatedValue;
							});
					iterableList.add(countConvertIterable);
				}
				final CombinedIterable<Map<String, Object>> combinedIterable = new CombinedIterable<Map<String, Object>>(
						iterableList);
				handler.execute(connection, combinedIterable);
				resultSetValueMapList.clear();
			}
		}
	}

	private void copyInsertAll(Connection connection, final Dialect dialect,
			final SqlParameterCollection sqlParameterCollection, final Table startTable, final Table table,
			final long rowAmplificationFactor, final List<SqlNode> insertSqlNodes, final long totalRows,
			final TableGeneratorConfig tableConfig) throws SQLException {
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, sqlParameterCollection)) {
			dialect.setFetchSizeForStream(statement, fetchSize);
			long[] readRowCount = new long[1];
			final long[] updatedRowCount = new long[1];
			final List<Map<String, Object>> valueList = CommonUtils.list();
			final long commitInterval = this.getQueryCommitInterval();
			final CommitCountHolder commitCountHandler = new CommitCountHolder(commitInterval, conn -> commit(conn));
			final JdbcBatchIterateHander handler = createJdbcBatchIterateHander(connection, dialect, table,
					insertSqlNodes, totalRows, readRowCount, updatedRowCount, o -> {
						@SuppressWarnings("unchecked")
						final Map<String, Object> vals = (Map<String, Object>) o;
						final ParametersContext context = new ParametersContext(table, vals);
						context.putAll(this.getContext());
						return context;
					}, conn -> {
					});
			try (final ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					final Map<String, Object> resultSetValueMap = CommonUtils.upperMap();
					for (int j = 0; j < startTable.getColumns().size(); j++) {
						final Object obj = resultSet.getObject(j + 1);
						final Column column = startTable.getColumns().get(j);
						resultSetValueMap.put(column.getName(), obj);
					}
					tableConfig.setSqlStartValue(resultSetValueMap);
					final Map<String, Object> map = CommonUtils.first(tableConfig.getDataSource());
					final Map<String, Object> covertedColumnMapping = tableConfig.convertColumnMapping(map);
					final Map<String, Object> generatedValue = tableConfig.generateValue(readRowCount[0],
							readRowCount[0]);
					getRowMonitor().handle(tableConfig, resultSetValueMap, readRowCount[0], 0, 0, updatedRowCount[0],
							covertedColumnMapping, generatedValue, startTable, table, insertSqlNodes);
					if (covertedColumnMapping != null) {
						generatedValue.putAll(covertedColumnMapping);
					}
					valueList.add(generatedValue);
					if (valueList.size() >= batchSize) {
						handler.execute(connection, valueList);
						valueList.clear();
						commitCountHandler.commit(connection);
					}
					readRowCount[0]++;
				}
				if (!CommonUtils.isEmpty(valueList)) {
					handler.execute(connection, valueList);
					commitCountHandler.finalCommit(connection);
					valueList.clear();
				}
			}
		}
	}

	private List<SqlNode> createSqlNode(Dialect dialect, final SqlConverter sqlConverter, String sql) {
		final SqlSplitter splitter = dialect.createSqlSplitter();
		final List<SplitResult> splitResults = splitter.parse(sql);
		final List<SqlNode> nodes = splitResults.stream().map(c -> {
			final ParametersContext context = new ParametersContext();
			context.putAll(this.getContext());
			if (c.getTextType().isComment() || CommonUtils.isBlank(c.getText())) {
				return null;
			}
			final SqlNode sqlNode = sqlConverter.parseSql(dialect, context, c.getText());
			return sqlNode;
		}).filter(c -> c != null).collect(Collectors.toList());
		return nodes;
	}

	private JdbcBatchIterateHander createJdbcBatchIterateHander(final Connection connection, final Dialect dialect,
			final Table table, final List<SqlNode> nodes, final long total, long[] generatedCount,
			final long[] updatedRowCount, Function<Object, Object> valueConverter,
			SQLConsumer<Connection> commitHandler) {
		final long oneper = total / 100;
		final long[] pointTime1 = new long[] { System.currentTimeMillis() };
		final JdbcBatchIterateHander handler = new JdbcBatchIterateHander(nodes,
				this.getTableOptions().getDmlBatchSize().apply(table), this.getQueryCommitInterval());
		handler.setValueConverter(valueConverter);
		handler.setCommitHandler(commitHandler);
		Set<Long> percentageCache = CommonUtils.set();
		handler.setBatchUpdateResultHandler(result -> {
			updatedRowCount[0] = updatedRowCount[0] + result.getValues().size();
			// INSERTで生成されたキーを反映する
			final int max = result.getGeneratedKeys().size();
			for (int i = 0; i < max; i++) {
				final GeneratedKeyInfo gk = result.getGeneratedKeys().get(i);
				@SuppressWarnings("unchecked")
				final Map<String, Object> vals = (Map<String, Object>) result.getValues().get(i).value();
				vals.put(gk.getColumnName(), gk.getValue());
				vals.put(gk.getColumnLabel(), gk.getValue());
			}
			debug("execute query batch size=[", result.getResult().length, "]. [", result.getMillis(), " ms]");
			long percentage = (generatedCount[0] * 100 / total);
			if (percentageCache.contains(percentage)) {
				return;
			}
			if (oneper == 0) {
				percentageCache.add(100L);
				long pointTime2 = System.currentTimeMillis();
				info("100% update completed.[", String.format("%3s", (generatedCount[0])), "/", total, "]. [",
						(pointTime2 - pointTime1[0]), " ms]");
				pointTime1[0] = pointTime2;
			} else {
				if (((generatedCount[0] + 1) % oneper) == 0) {
					percentageCache.add(percentage);
					long pointTime2 = System.currentTimeMillis();
					info(String.format("%3s", percentage), "% update completed.[",
							String.format("%3s", (generatedCount[0])), "/", total, "]. [", (pointTime2 - pointTime1[0]),
							" ms]");
					pointTime1[0] = pointTime2;
				}
			}
		});
		return handler;
	}

	private void executeSql(final Connection connection, final Dialect dialect, final Table table, String type,
			String sql) throws SQLException {
		execute(table.getName() + " " + type, () -> {
			final SqlSplitter sqlSplitter = dialect.createSqlSplitter();
			final SqlConverter sqlConverter = getSqlConverter();
			executeSql(sqlSplitter, sqlConverter, dialect, connection, sql);
		});
	}

	private void execute(String type, SQLRunnable run) throws SQLException {
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		info(MESSAGE_SEPARATOR_START, type, " start. start=[", startLocalTime, "].", MESSAGE_SEPARATOR_END);
		try {
			run.run();
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			info(MESSAGE_SEPARATOR_START, type, " completed. end=[", endLocalTime, "]. [", (end - start), " ms].",
					MESSAGE_SEPARATOR_END);
		} catch (RuntimeException e) {
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			error(e, MESSAGE_SEPARATOR_START, type, " errored. end=[", endLocalTime, "]. [", (end - start), " ms].",
					MESSAGE_SEPARATOR_END);
			throw e;
		}
	}

	private void executeSql(final SqlSplitter sqlSplitter, final SqlConverter sqlConverter, final Dialect dialect,
			final Connection connection, final String sql) throws SQLException {
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		final List<SplitResult> sqls = sqlSplitter.parse(sql);
		for (final SplitResult splitResult : sqls) {
			if (!splitResult.getTextType().isComment()) {
				info(splitResult.getText());
				executeSql(sqlConverter, dialect, connection, splitResult);
			}
		}
	}

	private void executeSql(final SqlConverter sqlConverter, final Dialect dialect, final Connection connection,
			final SplitResult splitResult) throws SQLException {
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		final SqlNode sqlNode = sqlConverter.parseSql(dialect, context, splitResult.getText());
		final OutputFormatType outputFormatType = OutputFormatType.TSV;
		final Table table = new Table();
		table.setDialect(dialect);
		final JdbcHandler jdbcHandler = dialect.createJdbcHandler(sqlNode, rs -> {
			boolean first = false;
			if (table.getColumns().size() == 0) {
				table.readMetaData(rs);
				final StringBuilder builder = new StringBuilder();
				for (final Column column : table.getColumns()) {
					builder.append(column.getName());
					builder.append(outputFormatType.getSeparator());
				}
				this.info(builder.substring(0, builder.length() - 1));
				first = true;
			}
			final StringBuilder builder = new StringBuilder();
			final int size = table.getColumns().size();
			for (int i = 1; i <= size; i++) {
				final Object obj = rs.getObject(i);
				final Column column = table.getColumns().get(i - 1);
				final String text = dialect.getValueForDisplay(column, obj);
				builder.append(text);
				builder.append(outputFormatType.getSeparator());
			}
			String text = builder.substring(0, builder.length() - 1);
			if (first) {
				this.info(getCharText("-", 10));
			}
			this.info(text);
		});
		jdbcHandler.execute(connection, context);
	}

	private String getCharText(String text, int len) {
		final StringBuilder builder = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			builder.append(text);
		}
		return builder.toString();
	}

	private Map<String, List<TableGeneratorConfig>> readConfig()
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		final Map<String, List<TableGeneratorConfig>> ret = CommonUtils.caseInsensitiveMap();
		if (this.files != null) {
			readConfig(files, ret);
			return ret;
		}
		if (this.getDirectory() == null) {
			return Collections.emptyMap();
		}
		final File[] files = this.getDirectory().listFiles();
		if (files == null) {
			return Collections.emptyMap();
		}
		readConfig(files, ret);
		return ret;
	}

	private void readConfig(File[] files, Map<String, List<TableGeneratorConfig>> ret)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		if (files == null) {
			return;
		}
		List<File> fileList = List.of(files);
		readConfig(fileList, ret);
	}

	private void readConfig(List<File> files, Map<String, List<TableGeneratorConfig>> ret)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		for (File file : files) {
			if (isUseSchemaNameDirectory()) {
				if (!file.isDirectory()) {
					continue;
				}
				final File[] children = file.listFiles();
				if (children == null) {
					continue;
				}
				for (File child : children) {
					addTableGeneratorConfig(child, ret);
				}
			} else {
				addTableGeneratorConfig(file, ret);
			}
		}
	}

	protected void addTableGeneratorConfig(File file, final Map<String, List<TableGeneratorConfig>> map) {
		if (!this.getFileFilter().test(file)) {
			return;
		}
		final TableGeneratorConfig config = this.getGeneratorConfigFactory().fromFile(file);
		if (config != null) {
			addTableGeneratorConfig(config, map);
		}
	}

	protected void addTableGeneratorConfig(TableGeneratorConfig tableConfig,
			final Map<String, List<TableGeneratorConfig>> map) {
		List<TableGeneratorConfig> list = map.get(tableConfig.getName());
		if (list == null) {
			list = CommonUtils.list();
			map.put(tableConfig.getName(), list);
		}
		list.add(tableConfig);
	}

	protected SqlConverter getSqlConverter() {
		final SqlConverter sqlConverter = new SqlConverter();
		return sqlConverter;
	}

	/**
	 * JDBCのバッチ実行のサイズを設定します
	 * 
	 * @param batchSize JDBCのバッチ実行のサイズ
	 */
	@Override
	public void setDmlBatchSize(int batchSize) {
		this.getTableOptions().setDmlBatchSize(batchSize);
	}

	public static class RowMonitor {

		public RowMonitor() {
			this.internal = null;
		}

		protected final RowMonitor internal;

		public RowMonitor(RowMonitor internal) {
			this.internal = internal;
		}

		public void handle(final TableGeneratorConfig tableConfig, final Map<String, Object> resultSetValueMap,
				long readRowCount, long dataSourceRowNumber, long generatedCount, long updatedRowCount,
				Map<String, Object> covertedColumnMapping, Map<String, Object> generatedValue, final Table startTable,
				final Table table, final List<SqlNode> insertSqlNodes) {
		}
	}
}
