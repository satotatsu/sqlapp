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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand.RowMonitor;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.properties.QueryCommitIntervalProperty;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Config and Data Insert Command
 */
@Getter
@Setter
public class GenerateDataConfigAndInsertCommand extends GenerateDataConfigCommand
		implements QueryCommitIntervalProperty {
	/** query commit interval */
	private long queryCommitInterval = Long.MAX_VALUE;
	private boolean isCloseDataSourceInternal;
	private int dmlBatchSize;
	private Consumer<TableGeneratorConfig> generatorConfigConsumer = t -> {
	};
	private RowMonitor rowMonitor = new RowMonitor() {
		@Override
		public void handle(final TableGeneratorConfig tableConfig, final Map<String, Object> resultSetValueMap,
				long readRowCount, long dataSourceRowNumber, long generatedCount, long updatedRowCount,
				Map<String, Object> covertedColumnMapping, Map<String, Object> generatedValue, final Table startTable,
				final Table table, final List<SqlNode> insertSqlNodes) {
			System.out.println("readRowCount=" + readRowCount + ", dataSourceRowNumber=" + dataSourceRowNumber);
			System.out.println("resultSetValueMap=" + resultSetValueMap);
			System.out.println("generatedValue=" + generatedValue);
		}
	};
	private BiConsumer<GenerateDataInsertCommand, List<File>> after = (command, files) -> {
	};

	@Override
	protected void initialize() {
		super.initializeContext();
		isCloseDataSourceInternal = isCloseDataSource();
		this.setCloseDataSource(false);
	}

	@Override
	protected void doRunAfter(List<File> files) {
		GenerateDataInsertCommand command = new GenerateDataInsertCommand();
		command.setCloseDataSource(isCloseDataSource());
		final Map<String, List<TableGeneratorConfig>> map = CommonUtils.map();
		for (File file : files) {
			final TableGeneratorConfig config = this.getGeneratorConfigFactory().fromFile(file);
			if (!command.getFileFilter().test(file)) {
				return;
			}
			generatorConfigConsumer.accept(config);
			command.addTableGeneratorConfig(config, map);
		}
		command.setTableConfigs(map);
		command.setDataSource(getDataSource());
		command.setQueryCommitInterval(this.getQueryCommitInterval());
		command.setDmlBatchSize(dmlBatchSize);
		command.setCommitLogEnabled(this.isCommitLogEnabled());
		command.setRowMonitor(this.getRowMonitor());
		command.run();
		after.accept(command, files);
		this.setCloseDataSource(isCloseDataSourceInternal);
	}

	/**
	 * JDBCのバッチ実行のサイズを設定します
	 * 
	 * @param dmlBatchSize JDBCのバッチ実行のサイズ
	 */
	public void setDmlBatchSize(int dmlBatchSize) {
		this.dmlBatchSize = dmlBatchSize;
	}
}
