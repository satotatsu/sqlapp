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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.mvel2.ParserContext;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.jdbc.sql.JdbcBatchUpdateHandler;
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
	/**
	 * SQL Type
	 */
	private SqlType sqlType = SqlType.INSERT;

	/** setting file directory */
	private File settingDirectory = new File("./");
	/** query commit interval */
	private long queryCommitInterval = Long.MAX_VALUE;

	/** table option */
	private TableOptions tableOptions = new TableOptions();
	/** 式評価 */
	private CachedEvaluator evaluator = new CachedMvelEvaluator();

	@Override
	protected void doRun() {
		if (this.evaluator == null) {
			CachedMvelEvaluator ceval = new CachedMvelEvaluator();
			ParserContext mvelParserContext = ParserContextFactory.getInstance().getParserContext();
			ceval.setParserContext(mvelParserContext);
			this.evaluator = ceval;
		}
		final Map<String, TableDataGeneratorSetting> tableSettings;
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
				final TableDataGeneratorSetting tableSetting = tableSettings.get(table.getName());
				if (tableSetting == null) {
					continue;
				}
				tableSetting.loadData(connection);
				tableSetting.setEvaluator(evaluator);
				tableSetting.calculateInitialValues();
				connection.setAutoCommit(false);
				applyFromFileByRow(connection, dialect, table, tableSetting);
				tableSettings.remove(table.getName());
				connection.setAutoCommit(true);
			}
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		}
	}

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final TableDataGeneratorSetting tableSetting) throws Exception {
		SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(tableOptions.clone());
		sqlFactoryRegistry.getOption().getTableOptions().setInsertableColumn(c -> {
			// INSERTから除外するカラムを設定
			ColumnDataGeneratorSetting colSetting = tableSetting.getColumns().get(c.getName());
			if (colSetting == null || colSetting.isInsertExclude()) {
				return false;
			}
			return true;
		});
		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, this.getSqlType());
		long queryCount = 0;
		final SqlConverter sqlConverter = getSqlConverter();
		final List<SqlOperation> operations = factory.createSql(table);
		final long total = tableSetting.getNumberOfRows();
		final LocalDateTime startLocalTime = LocalDateTime.now();
		final long start = System.currentTimeMillis();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		final List<ParametersContext> batchRows = CommonUtils.list(batchSize);
		try {
			final List<JdbcBatchUpdateHandler> handlers = operations.stream().map(c -> {
				final ParametersContext context = new ParametersContext();
				context.putAll(this.getContext());
				final SqlNode sqlNode = sqlConverter.parseSql(context, c.getSqlText());
				final JdbcBatchUpdateHandler jdbcHandler = new JdbcBatchUpdateHandler(sqlNode);
				jdbcHandler.setDialect(dialect);
				return jdbcHandler;
			}).collect(Collectors.toList());
			final long oneper = total / 100;
			long pointTime1 = System.currentTimeMillis();
			info("==== ", table.getName(), " insert start. numberOfRows=[" + total + "]. batchSize=[", batchSize,
					"]. start=[", startLocalTime, "]. ==== ");
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
		long end = System.currentTimeMillis();
		LocalDateTime endLocalTime = LocalDateTime.now();
		info("==== ", table.getName(), " insert completed. numberOfRows=[", total, "]. start=[", startLocalTime,
				"]. end=[", endLocalTime, "]. [", (end - start), " ms]. ==== ");
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

	private Map<String, TableDataGeneratorSetting> readSetting()
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		Map<String, TableDataGeneratorSetting> ret = CommonUtils.caseInsensitiveMap();
		for (File file : settingDirectory.listFiles((dir, f) -> f.endsWith(".xlsx"))) {
			try (Workbook wb = WorkbookFileType.createWorkBook(file, null, true)) {
				TableDataGeneratorSetting setting = GeneratorSettingWorkbook.readWorkbook(wb);
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
