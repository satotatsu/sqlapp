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
import com.sqlapp.jdbc.sql.ResultSetConcurrency;
import com.sqlapp.jdbc.sql.ResultSetHoldability;
import com.sqlapp.jdbc.sql.ResultSetType;
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
	/** Count source rows before inserting to report exact percentage progress. */
	private boolean countRowsBeforeInsert = true;
	/** Generate sample rows without executing INSERT or lifecycle SQL hooks. */
	private boolean preview = false;
	/** Maximum sample rows per table generator configuration. */
	private int previewLimit = 10;
	@Getter(lombok.AccessLevel.NONE)
	@Setter(lombok.AccessLevel.NONE)
	private final List<GeneratorPreviewRow> previewRows = CommonUtils.list();
	@Getter(lombok.AccessLevel.NONE)
	@Setter(lombok.AccessLevel.NONE)
	private final List<GeneratorPreviewDiagnostic> previewDiagnostics = CommonUtils.list();

	public List<GeneratorPreviewDiagnostic> getPreviewDiagnostics() {
		return List.copyOf(previewDiagnostics);
	}

	public List<GeneratorPreviewRow> getPreviewRows() {
		return List.copyOf(previewRows);
	}

	private Long randomSeed;
	private LocalDateTime baseTime;

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
		previewRows.clear();
		previewDiagnostics.clear();
		validatePreviewOptions();
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
		executeGenerator(connection -> {
			final Dialect dialect = this.getDialect(connection);
			final SchemaReader schemaReader = getSchemaReader(connection, dialect);
			final Map<String, Schema> schemaMap = this.getSchemas(connection, dialect, schemaReader, s -> true);
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
			final Map<Table, List<TableGeneratorConfig>> resolved = resolveTableConfigs(sorted, tableConfigs);
			// Resolve and validate every selected configuration before executing generation
			// SQL.
			for (Table table : sorted) {
				for (TableGeneratorConfig config : resolved.getOrDefault(table, Collections.emptyList())) {
					config.check();
					config.initializeTableColumnData(table);
				}
			}
			if (!preview) {
				connection.setAutoCommit(false);
			}
			for (final Table table : sorted) {
				for (TableGeneratorConfig config : resolved.getOrDefault(table, Collections.emptyList())) {
					info("");
					applyFromFileByRow(connection, dialect, table, config);
				}
			}
		});
	}

	private void validatePreviewOptions() {
		if (preview && previewLimit <= 0) {
			throw new IllegalArgumentException("previewLimit must be greater than zero");
		}
	}

	private void executeGenerator(com.sqlapp.jdbc.function.ExceptionConsumer<Connection> action) {
		if (preview) {
			executeNoTranAndClose(getDataSource(), action);
		} else {
			execute(getDataSource(), action);
		}
	}

	/**
	 * Resolve against selected Schema tables without mutating caller-owned
	 * configurations.
	 */
	Map<Table, List<TableGeneratorConfig>> resolveTableConfigs(List<Table> tables,
			Map<String, List<TableGeneratorConfig>> configs) {
		final Map<Table, List<TableGeneratorConfig>> resolved = new java.util.IdentityHashMap<>();
		for (List<TableGeneratorConfig> list : configs.values()) {
			for (TableGeneratorConfig config : list) {
				if (CommonUtils.isBlank(config.getName())) {
					throw new TableNotFoundException("Generator table name is required. file=" + config.getFile());
				}
				String schemaName = config.getSchemaName();
				if (isUseSchemaNameDirectory()) {
					File parent = config.getFile() == null ? null : config.getParentDirectory();
					if (parent != null) {
						if (!CommonUtils.isBlank(schemaName)
								&& !CommonUtils.eqIgnoreCase(schemaName, parent.getName())) {
							throw new IllegalArgumentException(
									"Generator schemaName=" + schemaName + " conflicts with schema directory="
											+ parent.getName() + ". file=" + config.getFile());
						}
						if (CommonUtils.isBlank(schemaName)) {
							schemaName = parent.getName();
						}
					} else if (CommonUtils.isBlank(schemaName)) {
						throw new IllegalArgumentException(
								"Generator requires schemaName or a file in a schema directory. table="
										+ config.getName());
					}
				}
				final String schema = schemaName;
				List<Table> candidates = tables.stream()
						.filter(t -> CommonUtils.eqIgnoreCase(t.getName(), config.getName()))
						.filter(t -> CommonUtils.isBlank(schema) || CommonUtils.eqIgnoreCase(t.getSchemaName(), schema))
						.collect(Collectors.toList());
				// Preserve literal identifier case where the metadata supplies an exact match.
				List<Table> exact = candidates.stream().filter(t -> CommonUtils.eq(t.getName(), config.getName()))
						.filter(t -> CommonUtils.isBlank(schema) || CommonUtils.eq(t.getSchemaName(), schema))
						.collect(Collectors.toList());
				if (!exact.isEmpty()) {
					candidates = exact;
				}
				if (candidates.isEmpty()) {
					Table target = new Table(config.getName());
					target.setSchemaName(schema);
					if (!getMetadataReaderFilter().test(target, null) || (!CommonUtils.isBlank(schema)
							&& !getMetadataReaderFilter().test(new Schema(schema), null))) {
						continue;
					}
					throw new TableNotFoundException("Generator target not found among selected tables. schemaName="
							+ schema + ", tableName=" + config.getName() + ", file=" + config.getFile()
							+ ". Check schemaName and table/schema selection filters.");
				}
				if (candidates.size() > 1) {
					String identities = candidates.stream().map(t -> "[catalog=" + t.getCatalogName() + ", schema="
							+ t.getSchemaName() + ", table=" + t.getName() + "]").collect(Collectors.joining(", "));
					throw new MultiTableFoundException("Ambiguous generator target. tableName=" + config.getName()
							+ ", file=" + config.getFile() + ", candidates=" + identities
							+ ". Specify schemaName or use schema directories; restrict the catalog if necessary.");
				}
				resolved.computeIfAbsent(candidates.get(0), t -> CommonUtils.list()).add(config);
			}
		}
		return resolved;
	}

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final TableGeneratorConfig tableConfig) throws SQLException {
		validatePreviewOptions();
		tableConfig.setEvaluator(evaluator);
		if (randomSeed != null) {
			tableConfig.setRandomSeed(randomSeed);
		}
		if (baseTime != null) {
			tableConfig.setBaseTime(baseTime);
		}
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		final long rowAmplificationFactor = !preview && countRowsBeforeInsert ? tableConfig.iterateCount() : -1;
		info(LOG_SEPARATOR_START, table.getName(), preview ? " Preview start." : " Insert start.", " batchSize=[",
				batchSize, "]. start=[", startLocalTime, "].", LOG_SEPARATOR_END);
		if (!preview && !CommonUtils.isBlank(tableConfig.getStartCountSql())) {
			executeSql(connection, dialect, table, "Start Select count", tableConfig.getStartCountSql());
		}
		info(MESSAGE_SEPARATOR_START,
				"rowAmplificationFactor=[" + (rowAmplificationFactor < 0 ? "unknown" : rowAmplificationFactor) + "]",
				MESSAGE_SEPARATOR_END);
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
		tableConfig.validateLoadedData();
		tableConfig.calculateInitialObjectValues();
		if (preview) {
			preview(connection, dialect, table, tableConfig);
			return;
		}
		final SqlConverter sqlConverter = getSqlConverter();
		final List<SqlNode> insertSqlNodes;
		final String insertSql;
		if (CommonUtils.isBlank(tableConfig.getInsertSql())) {
			insertSql = this.getGeneratorConfigFactory().createInsertSql(table, dialect, this.getTableOptions(),
					SqlType.INSERT, true);
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
		sqlParameterCollection.setResultSetType(ResultSetType.TYPE_FORWARD_ONLY);
		sqlParameterCollection.setResultSetConcurrency(ResultSetConcurrency.CONCUR_READ_ONLY);
		sqlParameterCollection.setResultSetHoldability(ResultSetHoldability.HOLD_CURSORS_OVER_COMMIT);
		if (countRowsBeforeInsert) {
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
		}
		final long total = countRowsBeforeInsert ? Math.multiplyExact(rowAmplificationFactor, startValueCounter[0])
				: -1;
		if (!CommonUtils.isBlank(tableConfig.getInitializeSql())) {
			executeSql(connection, dialect, table, "Initialize SQL", tableConfig.getInitializeSql());
		}
		final long[] processedRows = new long[1];
		execute(table.getName() + " Insert SQL", () -> {
			try {
				info(insertSql);
				if (rowAmplificationFactor == 0) {
					return;
				}
				if (rowAmplificationFactor != 1) {
					processedRows[0] = insertAll(connection, dialect, sqlParameterCollection, startTable, table,
							rowAmplificationFactor, insertSqlNodes, total, tableConfig);
				} else {
					processedRows[0] = copyInsertAll(connection, dialect, sqlParameterCollection, startTable, table,
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
		info(LOG_SEPARATOR_START, table.getName(), " Insert completed. numberOfRows=[", processedRows[0], "]. start=[",
				startLocalTime, "]. end=[", endLocalTime, "]. [", (end - start), " ms]. rowsPerSecond=[",
				String.format(java.util.Locale.ROOT, "%.1f", processedRows[0] * 1000.0 / Math.max(1, end - start)),
				"].", LOG_SEPARATOR_END);
	}

	private long insertAll(Connection connection, final Dialect dialect,
			final SqlParameterCollection sqlParameterCollection, final Table startTable, final Table table,
			final long rowAmplificationFactor, final List<SqlNode> insertSqlNodes, final long totalRows,
			final TableGeneratorConfig tableConfig) throws SQLException {
		int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		info("rowAmplificationFactor=" + (rowAmplificationFactor < 0 ? "unknown" : rowAmplificationFactor)
				+ ", batchSize=" + batchSize);
		final long[] generatedCount = new long[1];
		try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, sqlParameterCollection)) {
			dialect.setFetchSizeForStream(statement, fetchSize);
			final long[] readRowCount = new long[1];
			final long[] updatedRowCount = new long[1];
			List<Map<String, Object>> resultSetValueMapList = CommonUtils.list();
			try (final JdbcBatchIterateHander handler = createJdbcBatchIterateHander(connection, dialect, table,
					insertSqlNodes, totalRows, generatedCount, updatedRowCount, o -> {
						@SuppressWarnings("unchecked")
						final Map<String, Object> vals = (Map<String, Object>) o;
						final ParametersContext context = new ParametersContext(table, vals);
						context.putAll(this.getContext());
						generatedCount[0]++;
						return context;
					}, conn -> {
						commit(conn);
					});) {
				try (final ResultSet resultSet = statement.executeQuery()) {
					startTable.readMetaData(resultSet);
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
								final long startRowIndex = readRowCount[0] - resultSetValueMapList.size() + 1 + j;
								final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> countConvertIterable = new IndexedConvertIterable<>(
										() -> {
											tableConfig.setSqlStartValue(currentResultSetValueMap);
											final Iterable<Map<String, Object>> dataSourceIterable = tableConfig
													.getDataSource();
											return dataSourceIterable;
										}, (i, map) -> {
											try {
												final GeneratedRow row = generateRow(tableConfig, map, startRowIndex,
														generatedCount[0]);
												final Map<String, Object> covertedColumnMapping = row.mapping();
												final Map<String, Object> generatedValue = row.values();
												getRowMonitor().handle(tableConfig, currentResultSetValueMap,
														readRowCount[0], i, generatedCount[0], updatedRowCount[0],
														covertedColumnMapping, generatedValue, startTable, table,
														insertSqlNodes);
												if (covertedColumnMapping != null) {
													generatedValue.putAll(covertedColumnMapping);
												}
												return generatedValue;
											} catch (RuntimeException e) {
												throw rowFailure(table, startRowIndex, i, generatedCount[0], e);
											}
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
						final long startRowIndex = readRowCount[0] - resultSetValueMapList.size() + j;
						final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> countConvertIterable = new IndexedConvertIterable<>(
								() -> {
									tableConfig.setSqlStartValue(currentResultSetValueMap);
									final Iterable<Map<String, Object>> dataSourceIterable = tableConfig
											.getDataSource();
									return dataSourceIterable;
								}, (i, map) -> {
									try {
										final GeneratedRow row = generateRow(tableConfig, map, startRowIndex,
												generatedCount[0]);
										final Map<String, Object> covertedColumnMapping = row.mapping();
										final Map<String, Object> generatedValue = row.values();
										getRowMonitor().handle(tableConfig, currentResultSetValueMap, readRowCount[0],
												i, generatedCount[0], updatedRowCount[0], covertedColumnMapping,
												generatedValue, startTable, table, insertSqlNodes);
										if (covertedColumnMapping != null) {
											generatedValue.putAll(covertedColumnMapping);
										}
										return generatedValue;
									} catch (RuntimeException e) {
										throw rowFailure(table, startRowIndex, i, generatedCount[0], e);
									}
								});
						iterableList.add(countConvertIterable);
					}
					final CombinedIterable<Map<String, Object>> combinedIterable = new CombinedIterable<Map<String, Object>>(
							iterableList);
					handler.execute(connection, combinedIterable);
					resultSetValueMapList.clear();
				}
			}
		} catch (SQLException e) {
			throw sqlFailure(table, generatedCount[0], e);
		}
		return generatedCount[0];
	}

	private long copyInsertAll(Connection connection, final Dialect dialect,
			final SqlParameterCollection sqlParameterCollection, final Table startTable, final Table table,
			final long rowAmplificationFactor, final List<SqlNode> insertSqlNodes, final long totalRows,
			final TableGeneratorConfig tableConfig) throws SQLException {
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		info("rowAmplificationFactor=" + (rowAmplificationFactor < 0 ? "unknown" : rowAmplificationFactor)
				+ ", batchSize=" + batchSize);
		final long[] generatedCount = new long[1];
		try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, sqlParameterCollection)) {
			dialect.setFetchSizeForStream(statement, fetchSize);
			long[] readRowCount = new long[1];
			final long[] updatedRowCount = new long[1];
			final List<Map<String, Object>> valueList = CommonUtils.list();
			final long commitInterval = this.getQueryCommitInterval();
			final CommitCountHolder commitCountHandler = new CommitCountHolder(commitInterval, conn -> commit(conn));
			try (final ResultSet resultSet = statement.executeQuery();
					final JdbcBatchIterateHander handler = createJdbcBatchIterateHander(connection, dialect, table,
							insertSqlNodes, totalRows, generatedCount, updatedRowCount, o -> {
								@SuppressWarnings("unchecked")
								final Map<String, Object> vals = (Map<String, Object>) o;
								final ParametersContext context = new ParametersContext(table, vals);
								context.putAll(this.getContext());
								generatedCount[0]++;
								return context;
							}, conn -> {
							});) {
				startTable.readMetaData(resultSet);
				while (resultSet.next()) {
					final Map<String, Object> resultSetValueMap = CommonUtils.upperMap();
					for (int j = 0; j < startTable.getColumns().size(); j++) {
						final Object obj = resultSet.getObject(j + 1);
						final Column column = startTable.getColumns().get(j);
						resultSetValueMap.put(column.getName(), obj);
					}
					final Map<String, Object> generatedValue;
					java.util.Iterator<Map<String, Object>> iterator = null;
					Throwable failure = null;
					try {
						tableConfig.setSqlStartValue(resultSetValueMap);
						iterator = tableConfig.getDataSource().iterator();
						final Map<String, Object> map = CommonUtils.first(iterator);
						final GeneratedRow row = generateRow(tableConfig, map, readRowCount[0], readRowCount[0]);
						final Map<String, Object> covertedColumnMapping = row.mapping();
						generatedValue = row.values();
						getRowMonitor().handle(tableConfig, resultSetValueMap, readRowCount[0], 0, 0,
								updatedRowCount[0], covertedColumnMapping, generatedValue, startTable, table,
								insertSqlNodes);
						if (covertedColumnMapping != null) {
							generatedValue.putAll(covertedColumnMapping);
						}
					} catch (RuntimeException e) {
						failure = e;
						throw rowFailure(table, readRowCount[0], 0, generatedCount[0], e);
					} catch (Error e) {
						failure = e;
						throw e;
					} finally {
						com.sqlapp.util.iterator.IteratorCloseUtils.close(failure, iterator);
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
		} catch (SQLException e) {
			throw sqlFailure(table, generatedCount[0], e);
		}
		return generatedCount[0];
	}

	private record GeneratedRow(Map<String, Object> mapping, Map<String, Object> values) {
	}

	private GeneratedRow generateRow(TableGeneratorConfig config, Map<String, Object> input, long rowNumber,
			long generationIndex) {
		final Map<String, Object> mapping = config.convertColumnMapping(input);
		return new GeneratedRow(mapping, config.generateValue(rowNumber, generationIndex));
	}

	private void preview(Connection connection, Dialect dialect, Table table, TableGeneratorConfig config)
			throws SQLException {
		final ParametersContext context = new ParametersContext();
		final SqlNode node = getSqlConverter().parseSql(dialect, context, config.getStartValueSql());
		final SqlParameterCollection parameters = node.eval(context);
		parameters.setFetchSize(fetchSize);
		parameters.setResultSetType(ResultSetType.TYPE_FORWARD_ONLY);
		parameters.setResultSetConcurrency(ResultSetConcurrency.CONCUR_READ_ONLY);
		long generated = 0;
		long startIndex = 0;
		try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection, parameters)) {
			dialect.setFetchSizeForStream(statement, fetchSize);
			try (final ResultSet results = statement.executeQuery()) {
				final Table startTable = new Table();
				startTable.readMetaData(results);
				while (generated < previewLimit && results.next()) {
					final Map<String, Object> startValues = CommonUtils.upperMap();
					for (int j = 0; j < startTable.getColumns().size(); j++) {
						startValues.put(startTable.getColumns().get(j).getName(), results.getObject(j + 1));
					}
					config.setSqlStartValue(startValues);
					java.util.Iterator<Map<String, Object>> iterator = null;
					Throwable failure = null;
					long iterateIndex = 0;
					try {
						iterator = config.getDataSource().iterator();
						while (generated < previewLimit && iterator.hasNext()) {
							final GeneratedRow row = generateRow(config, iterator.next(), startIndex, generated);
							if (row.mapping() != null) {
								row.values().putAll(row.mapping());
							}
							final Map<String, Object> columnValues = CommonUtils.linkedMap();
							for (Column column : table.getColumns()) {
								if (row.values().containsKey(column.getName())) {
									columnValues.put(column.getName(), row.values().get(column.getName()));
								}
							}
							final GeneratorPreviewRow sample = new GeneratorPreviewRow(table.getCatalogName(),
									table.getSchemaName(), table.getName(), config.getFile(), startIndex, iterateIndex,
									columnValues);
							previewRows.add(sample);
							for (GeneratorPreviewDiagnostic diagnostic : GeneratorPreviewValidator.validate(table,
									sample)) {
								previewDiagnostics.add(diagnostic);
								info("Preview diagnostic table=", table.getSchemaName(), ".", table.getName(),
										", startRowIndex=", startIndex, ", iterateIndex=", iterateIndex, ", column=",
										diagnostic.columnName(), ", code=", diagnostic.code(), ", message=",
										diagnostic.message());
							}
							info("Preview table=", table.getSchemaName(), ".", table.getName(), ", startRowIndex=",
									startIndex, ", iterateIndex=", iterateIndex, ", values=", sample.values(),
									", types=", sample.valueTypes());
							generated++;
							iterateIndex++;
						}
					} catch (RuntimeException | Error e) {
						failure = e;
						if (e instanceof RuntimeException runtime) {
							throw rowFailure(table, startIndex, iterateIndex, generated, runtime);
						}
						throw e;
					} finally {
						com.sqlapp.util.iterator.IteratorCloseUtils.close(failure, iterator);
					}
					startIndex++;
				}
			}
		} catch (SQLException e) {
			throw new SQLException("Generator preview table=" + table.getSchemaName() + "." + table.getName()
					+ ", sampledRowCount=" + generated + ". " + e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
		}
		info("Preview completed. table=", table.getSchemaName(), ".", table.getName(), ", sampledRowCount=", generated,
				", limit=", previewLimit);
	}

	private SQLException sqlFailure(Table table, long convertedCount, SQLException cause) {
		final String message = "Generator table=" + table.getSchemaName() + "." + table.getName()
				+ ", phase=insert/startValueSql, convertedInputCount=" + convertedCount
				+ ". Exact failing row within a JDBC batch is unknown. " + cause.getMessage();
		if (cause instanceof java.sql.BatchUpdateException batch) {
			return new java.sql.BatchUpdateException(message, cause.getSQLState(), cause.getErrorCode(),
					batch.getLargeUpdateCounts(), cause);
		}
		return new SQLException(message, cause.getSQLState(), cause.getErrorCode(), cause);
	}

	private RuntimeException rowFailure(Table table, long startRow, long iterateIndex, long convertedCount,
			RuntimeException cause) {
		return new IllegalStateException("Generator table=" + table.getSchemaName() + "." + table.getName()
				+ ", startRowIndex=" + startRow + ", iterateIndex=" + iterateIndex + ", convertedInputCount="
				+ convertedCount + " (indexes are zero-based). " + cause.getMessage(), cause);
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
		final GeneratorProgress progress = new GeneratorProgress(total, System::nanoTime);
		final JdbcBatchIterateHander handler = new JdbcBatchIterateHander(nodes,
				this.getTableOptions().getDmlBatchSize().apply(table), this.getQueryCommitInterval());
		handler.setValueConverter(valueConverter);
		handler.setCommitHandler(commitHandler);
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
			String message = progress.update(generatedCount[0]);
			if (message != null) {
				info(message);
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
