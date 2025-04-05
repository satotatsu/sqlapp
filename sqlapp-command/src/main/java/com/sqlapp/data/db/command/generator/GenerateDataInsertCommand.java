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
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.GeneratedKeyInfo;
import com.sqlapp.jdbc.sql.JdbcBatchIterateHander;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.JdbcHandlerUtils;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.CountIterable;
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
	private TableGeneratorSettingFactory generatorSettingFactory = new TableGeneratorSettingFactory();

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
			info("File not found. settingDirectory=" + settingDirectory.getAbsolutePath());
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

	private static final String LOG_SEPARATOR_START = "<<====== ";
	private static final String LOG_SEPARATOR_END = " ======>>";
	private static final String MESSAGE_SEPARATOR_START = "<- ";
	private static final String MESSAGE_SEPARATOR_END = " ->";

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final TableGeneratorSetting tableSetting) throws Exception {
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		info(LOG_SEPARATOR_START, table.getName(),
				" insert start. numberOfRows=[" + tableSetting.getNumberOfRows() + "]. batchSize=[", batchSize,
				"]. start=[", startLocalTime, "].", LOG_SEPARATOR_END);
		if (!CommonUtils.isBlank(tableSetting.getSetupSql())) {
			executeSql(connection, dialect, table, "start", tableSetting.getSetupSql());
		}
		setSelectStartValueSql(dialect, connection, tableSetting);
		final SqlConverter sqlConverter = getSqlConverter();
		final List<SqlNode> nodes;
		if (CommonUtils.isBlank(tableSetting.getInsertSql())) {
			final String insertSql = this.getGeneratorSettingFactory().createInsertSql(table, dialect,
					this.getTableOptions(), SqlType.INSERT);
			nodes = createSqlNode(dialect, sqlConverter, insertSql);
		} else {
			nodes = createSqlNode(dialect, sqlConverter, tableSetting.getInsertSql());
		}
		final ParametersContext contextForStartValue = new ParametersContext();
		SqlNode startValueSqlNode = sqlConverter.parseSql(contextForStartValue, tableSetting.getStartValueSql());
		final Table startTable = new Table();
		try {
			long startValueCounter = 0;
			final SqlParameterCollection sqlParameterCollection = startValueSqlNode.eval(contextForStartValue);
			try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection,
					sqlParameterCollection)) {
				statement.setFetchSize(1024);
				// 最初にStartQueryの対象行数だけ調べる
				try (final ResultSet resultSet = statement.executeQuery()) {
					startTable.readMetaData(resultSet);
					while (resultSet.next()) {
						startValueCounter++;
					}
				}
			}
			final long total = tableSetting.getNumberOfRows() * startValueCounter;
			long rowCount = 0;
			final long[] insertedRowCountHolder = new long[1];
			try (final PreparedStatement statement = JdbcHandlerUtils.getStatement(connection,
					sqlParameterCollection)) {
				try (final ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						final Map<String, Object> valueMap = CommonUtils.upperMap();
						for (int j = 0; j < startTable.getColumns().size(); j++) {
							final Object obj = resultSet.getObject(j + 1);
							final Column column = startTable.getColumns().get(j);
							valueMap.put(column.getName(), obj);
						}
						tableSetting.setSqlStartValue(valueMap);
						final CountIterable<Map<String, Object>> countIterable = new CountIterable<Map<String, Object>>(
								rowCount, rowCount + tableSetting.getNumberOfRows(), (i) -> {
									final Map<String, Object> vals = tableSetting.generateValue(i);
									return vals;
								});
						final JdbcBatchIterateHander handler = createJdbcBatchIterateHander(connection, dialect, table,
								nodes, total, tableSetting, insertedRowCountHolder);
						handler.execute(connection, countIterable);
						rowCount = rowCount + insertedRowCountHolder[0];
					}
				}
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
		info(LOG_SEPARATOR_START, table.getName(), " insert completed. numberOfRows=[", tableSetting.getNumberOfRows(),
				"]. start=[", startLocalTime, "]. end=[", endLocalTime, "]. [", (end - start), " ms].",
				LOG_SEPARATOR_END);
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
			final SqlNode sqlNode = sqlConverter.parseSql(context, c.getText());
			this.debug(sqlNode);
			return sqlNode;
		}).filter(c -> c != null).collect(Collectors.toList());
		return nodes;
	}

	private JdbcBatchIterateHander createJdbcBatchIterateHander(final Connection connection, final Dialect dialect,
			final Table table, final List<SqlNode> nodes, final long total, final TableGeneratorSetting tableSetting,
			final long[] insertedRowCountHolder) {
		final long oneper = total / 100;
		final long[] pointTime1 = new long[] { System.currentTimeMillis() };
		final JdbcBatchIterateHander handler = new JdbcBatchIterateHander(nodes,
				this.getTableOptions().getDmlBatchSize().apply(table), this.getQueryCommitInterval());
		handler.setValueConverter(o -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> vals = (Map<String, Object>) o;
			final ParametersContext context = convertDataType(vals, table);
			context.putAll(this.getContext());
			return context;
		});
		handler.setBatchUpdateResultHandler(result -> {
			insertedRowCountHolder[0] = result.getLastRowIndex() + 1;
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
			if (((result.getLastRowIndex() + 1) % oneper) == 0) {
				long pointTime2 = System.currentTimeMillis();
				info(String.format("%3s", ((result.getLastRowIndex() + 1) / oneper)), "% insert completed.[",
						String.format("%3s", (insertedRowCountHolder[0] + result.getLastRowIndex() + 1)), "/", total,
						"]. [", (pointTime2 - pointTime1[0]), " ms]");
				pointTime1[0] = pointTime2;
			}
		});
		handler.setCommitHandler(conn -> {
			conn.commit();
			this.debug("commit");
		});
		return handler;
	}

	private void executeSql(final Connection connection, final Dialect dialect, final Table table, String type,
			String sql) {
		final SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		final SqlConverter sqlConverter = getSqlConverter();
		final long start = System.currentTimeMillis();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		info(MESSAGE_SEPARATOR_START, table.getName(), " " + type + " SQL start. start=[", startLocalTime, "].",
				MESSAGE_SEPARATOR_END);
		try {
			executeSql(sqlSplitter, sqlConverter, dialect, connection, sql);
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			info(MESSAGE_SEPARATOR_START, table.getName(), " " + type + " SQL completed. end=[", endLocalTime, "]. [",
					(end - start), " ms].", MESSAGE_SEPARATOR_END);
		} catch (RuntimeException e) {
			LocalDateTime endLocalTime = LocalDateTime.now();
			long end = System.currentTimeMillis();
			error(e, MESSAGE_SEPARATOR_START, table.getName(), " " + type + " SQL errored. end=[", endLocalTime, "]. [",
					(end - start), " ms].", MESSAGE_SEPARATOR_END);
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
			final TableGeneratorSetting setting = this.getGeneratorSettingFactory().fromFile(file);
			if (setting != null) {
				ret.put(setting.getName(), setting);
			}
		}
		return ret;
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
