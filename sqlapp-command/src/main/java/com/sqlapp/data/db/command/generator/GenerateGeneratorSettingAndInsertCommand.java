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
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.properties.QueryCommitIntervalProperty;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Setting and Data Insert Command
 */
@Getter
@Setter
public class GenerateGeneratorSettingAndInsertCommand extends GenerateGeneratorSettingCommand
		implements QueryCommitIntervalProperty {
	/** query commit interval */
	private long queryCommitInterval = Long.MAX_VALUE;
	private boolean isCloseDataSourceInternal;
	private int dmlBatchSize;
	private Consumer<TableGeneratorSetting> tableSettingConsumer = t -> {
	};
	private RowMonitor rowMonitor = new RowMonitor();
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
		final Map<String, List<TableGeneratorSetting>> map = CommonUtils.map();
		for (File file : files) {
			final TableGeneratorSetting setting = this.getGeneratorSettingFactory().fromFile(file);
			if (!command.getFileFilter().test(file)) {
				return;
			}
			tableSettingConsumer.accept(setting);
			command.addTableGeneratorSetting(setting, map);
		}
		command.setTableSettings(map);
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
