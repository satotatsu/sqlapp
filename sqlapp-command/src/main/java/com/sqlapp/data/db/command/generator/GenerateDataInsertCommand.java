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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.mvel2.ParserContext;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.OutputFormatType;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcBatchUpdateHandler;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
import com.sqlapp.util.eval.mvel.ParserContextFactory;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class GenerateDataInsertCommand extends AbstractDataSourceCommand {
	/**
	 * schema name
	 */
	private String schemaName;
	/**
	 * table name
	 */
	private String tableName;

	/** setting file directory */
	private File settingDirectory = new File("./");
	/** query commit interval */
	private long queryCommitInterval = Long.MAX_VALUE;
	/** 式評価 */
	private CachedEvaluator evaluator = new CachedMvelEvaluator();
	/** table option */
	private TableOptions tableOptions = new TableOptions();
	/** TableDataGeneratorSettingFactory */
	private TableGeneratorSettingFactory generatorSeettingFactory = new TableGeneratorSettingFactory();

	@Override
	protected void doRun() {
		if (this.evaluator == null) {
			CachedMvelEvaluator ceval = new CachedMvelEvaluator();
			ParserContext mvelParserContext = ParserContextFactory.getInstance().getParserContext();
			ceval.setParserContext(mvelParserContext);
			this.evaluator = ceval;
		}
		final Map<String, TableGeneratorSetting> tableSettings;
		try {
			tableSettings = readSetting();
		} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
			throw new RuntimeException(e);
		}
		if (tableSettings.isEmpty()) {
			info("File not found. settingDirectory" + settingDirectory.getAbsolutePath());
			return;
		}
		Connection connection = null;
		try {
			connection = this.getConnection();
			final Dialect dialect = this.getDialect(connection);
			final CatalogReader catalogReader = dialect.getCatalogReader();
			final TableReader tableReader = catalogReader.getSchemaReader().getTableReader();
			tableReader.setSchemaName(this.getSchemaName());
			tableReader.setObjectName(this.getTableName());
			List<Table> tableList = tableReader.getAllFull(connection);
			if (tableList.isEmpty()) {
				throw new TableNotFoundException(
						"schemaName=" + this.getSchemaName() + ", tableName=" + getTableName());
			}
			if (tableList.isEmpty()) {
				throw new MultiTableFoundException("schemaName=" + this.getSchemaName() + ", tableName="
						+ getTableName() + ", tableSize=" + tableList.size());
			}
			for (final Table table : tableList) {
				final TableGeneratorSetting tableSetting = tableSettings.get(table.getName());
				if (tableSetting == null) {
					continue;
				}
				connection.setAutoCommit(false);
				tableSetting.loadData(connection);
				tableSetting.setEvaluator(evaluator);
				tableSetting.calculateInitialObejectValues();
				setSelectStartValueSql(dialect, connection, tableSetting);
				applyFromFileByRow(connection, dialect, table, tableSetting);
				tableSettings.remove(table.getName());
				connection.setAutoCommit(true);
			}
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		} finally {
			releaseConnection(connection);
		}
	}

	private void setSelectStartValueSql(final Dialect dialect, final Connection connection,
			final TableGeneratorSetting tableSetting) throws SQLException {
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		final List<SplitResult> sqls = sqlSplitter.parse(tableSetting.getStartValueSql());
		for (final SplitResult splitResult : sqls) {
			if (!splitResult.getTextType().isComment()) {
				debug(splitResult.getText());
			}
			try (final Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY)) {
				try (final ResultSet resultSet = statement.executeQuery(splitResult.getText())) {
					Table table = new Table();
					table.readData(resultSet);
					for (int i = 0; i < table.getColumns().size(); i++) {
						final ColumnGeneratorSetting colSet = tableSetting.getColumns()
								.get(table.getColumns().get(i).getName());
						if (colSet == null) {
							continue;
						}
						final Object val = resultSet.getObject(i + 1);
						if (val != null) {
							final Object valForType = colSet.getMaxValueObject() != null ? colSet.getMaxValueObject()
									: colSet.getStartValueObject();
							final Object valConverted;
							if (valForType != null) {
								valConverted = Converters.getDefault().convertObject(val, valForType.getClass());
							} else {
								valConverted = val;
							}
							colSet.setStartValueObject(valConverted);
						}
					}
				}
			}
		}
	}

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final TableGeneratorSetting tableSetting) throws Exception {
		final long start = System.currentTimeMillis();
		final long total = tableSetting.getNumberOfRows();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		info("==== ", table.getName(), " insert start. numberOfRows=[" + total + "]. batchSize=[", batchSize,
				"]. start=[", startLocalTime, "]. ==== ");
		if (!CommonUtils.isBlank(tableSetting.getSetupSql())) {
			executeSql(connection, dialect, table, "start", tableSetting.getSetupSql());
		}
		final SqlSplitter splitter = dialect.createSqlSplitter();
		final List<SplitResult> splitResults;
		if (CommonUtils.isEmpty(tableSetting.getInsertSql())) {
			splitResults = Collections.emptyList();
		} else {
			splitResults = splitter.parse(tableSetting.getInsertSql());
		}
		long queryCount = 0;
		final List<ParametersContext> batchRows = CommonUtils.list(batchSize);
		final SqlConverter sqlConverter = getSqlConverter();
		try {
			final List<JdbcBatchUpdateHandler> handlers = splitResults.stream().map(c -> {
				final ParametersContext context = new ParametersContext();
				context.putAll(this.getContext());
				if (c.getTextType().isComment() || CommonUtils.isBlank(c.getText())) {
					return null;
				}
				final SqlNode sqlNode = sqlConverter.parseSql(context, c.getText());
				this.debug(sqlNode);
				final JdbcBatchUpdateHandler jdbcHandler = new JdbcBatchUpdateHandler(sqlNode);
				jdbcHandler.setDialect(dialect);
				return jdbcHandler;
			}).filter(c -> c != null).collect(Collectors.toList());
			final long oneper = total / 100;
			long pointTime1 = System.currentTimeMillis();
			int batchRowSize;
			for (long i = 0; i < total; i++) {
				Map<String, Object> vals = tableSetting.generateValue(i);
				final ParametersContext context = convertDataType(vals, table);
				context.putAll(this.getContext());
				batchRows.add(context);
				batchRowSize = batchRows.size();
				if (((i + 1) % oneper) == 0) {
					long pointTime2 = System.currentTimeMillis();
					info(String.format("%3s", ((i + 1) / oneper)), "% insert completed.[",
							String.format("%3s", (i + 1)), "/", total, "]. [", (pointTime2 - pointTime1), " ms]");
					pointTime1 = pointTime2;
				}
				if (batchRowSize >= batchSize) {
					long pointTime3 = System.currentTimeMillis();
					for (final JdbcBatchUpdateHandler jdbcHandler : handlers) {
						jdbcHandler.execute(connection, batchRows);
						long pointTime4 = System.currentTimeMillis();
						debug("execute query batch size=[", batchRowSize, "]. [", (pointTime4 - pointTime3), " ms]");
						queryCount = commit(connection, queryCount);
						pointTime1 = pointTime4;
					}
					batchRows.clear();
				}
			}
			batchRowSize = batchRows.size();
			if (batchRowSize > 0) {
				for (final JdbcBatchUpdateHandler jdbcHandler : handlers) {
					jdbcHandler.execute(connection, batchRows);
				}
				debug("execute query batch size=", batchRowSize);
				commit(connection);
				batchRows.clear();
			}
		} catch (Exception e) {
			connection.rollback();
			throw e;
		} finally {
			table.setRowIteratorHandler(null);
		}
		if (!CommonUtils.isBlank(tableSetting.getFinalizeSql())) {
			executeSql(connection, dialect, table, "end", tableSetting.getSetupSql());
		}
		long end = System.currentTimeMillis();
		LocalDateTime endLocalTime = LocalDateTime.now();
		info("==== ", table.getName(), " insert completed. numberOfRows=[", total, "]. start=[", startLocalTime,
				"]. end=[", endLocalTime, "]. [", (end - start), " ms]. ==== ");
	}

	private void executeSql(final Connection connection, final Dialect dialect, final Table table, String type,
			String sql) {
		final SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		final SqlConverter sqlConverter = getSqlConverter();
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		info("==== ", table.getName(), " " + type + " SQL start. start=[", startLocalTime, "]. ==== ");
		try {
			executeSql(sqlSplitter, sqlConverter, dialect, connection, sql);
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			info("==== ", table.getName(), " " + type + " SQL completed. start=[", startLocalTime, "]. end=[",
					endLocalTime, "]. [", (end - start), " ms]. ==== ");
		} catch (RuntimeException e) {
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			info("==== ", table.getName(), " " + type + " SQL errored. start=[", startLocalTime, "]. end=[",
					endLocalTime, "]. [", (end - start), " ms]. ==== ");
			throw e;
		}
	}

	private void executeSql(final SqlSplitter sqlSplitter, final SqlConverter sqlConverter, final Dialect dialect,
			final Connection connection, final String sql) {
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		final List<SplitResult> sqls = sqlSplitter.parse(sql);
		for (final SplitResult splitResult : sqls) {
			if (!splitResult.getTextType().isComment()) {
				debug(splitResult.getText());
				executeSql(sqlConverter, dialect, connection, splitResult);
			}
		}
	}

	private void executeSql(final SqlConverter sqlConverter, final Dialect dialect, final Connection connection,
			final SplitResult splitResult) {
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		final SqlNode sqlNode = sqlConverter.parseSql(context, splitResult.getText());
		final OutputFormatType outputFormatType = OutputFormatType.TSV;
		final Table table = new Table();
		table.setDialect(dialect);
		final JdbcHandler jdbcHandler = dialect.createJdbcHandler(sqlNode, rs -> {
			boolean first = false;
			if (table.getColumns().size() == 0) {
				table.readMetaData(connection, rs);
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

	private ParametersContext convertDataType(Map<String, Object> map, Table table) {
		final ParametersContext context = new ParametersContext();
		context.putAll(map);
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			Column column = table.getColumns().get(entry.getKey());
			if (column != null) {
				Object val = Converters.getDefault().convertObject(entry.getValue(),
						column.getDataType().getDefaultClass());
				context.put(entry.getKey(), val);
			}
		}
		return context;
	}

	private Map<String, TableGeneratorSetting> readSetting()
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		if (this.getSettingDirectory() == null) {
			return Collections.emptyMap();
		}
		final File[] files = this.getSettingDirectory().listFiles();
		if (files == null) {
			return Collections.emptyMap();
		}
		final Map<String, TableGeneratorSetting> ret = CommonUtils.caseInsensitiveMap();
		for (File file : files) {
			final TableGeneratorSetting setting = this.getGeneratorSeettingFactory().fromFile(file);
			if (setting != null) {
				ret.put(setting.getName(), setting);
			}
		}
		return ret;
	}

	private long commit(final Connection connection, final long queryCount) throws SQLException {
		if ((queryCount + 1) >= this.getQueryCommitInterval()) {
			commit(connection);
			return 0;
		}
		return queryCount + 1;
	}

	private void commit(final Connection connection) throws SQLException {
		connection.commit();
		this.debug("commit");
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
	public void setDmlBatchSize(int batchSize) {
		this.getTableOptions().setDmlBatchSize(batchSize);
	}
}
