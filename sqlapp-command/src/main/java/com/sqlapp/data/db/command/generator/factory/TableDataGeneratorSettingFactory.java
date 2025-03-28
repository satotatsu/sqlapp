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

package com.sqlapp.data.db.command.generator.factory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.data.db.command.generator.GeneratorSettingWorkbook;
import com.sqlapp.data.db.command.generator.setting.ColumnDataGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryDefinitionDataGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableDataGeneratorSetting;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * TableDataGeneratorSetting Factory
 */
@Getter
@Setter
public class TableDataGeneratorSettingFactory {

	private ColumnFunction<String> columnStartValue = new ColumnStartValue();

	private ColumnFunction<String> columnNextValue = new ColumnNextValue();

	private ColumnFunction<String> columnMaxValue = new ColumnMaxValue();

	/**
	 * テーブルの値からTableDataGeneratorSettingを作成します
	 * 
	 * @param table   テーブル
	 * @param dialect DB Dialect
	 * @return TableDataGeneratorSetting
	 */
	public TableDataGeneratorSetting createDefault(Table table, Dialect dialect) {
		TableDataGeneratorSetting setting = new TableDataGeneratorSetting();
		setTableDefaultValues(table, dialect, setting);
		setColumnDefaultValues(table, dialect, setting);
		setQueryDefaultValue(table, dialect, setting);
		return setting;
	}

	public TableDataGeneratorSetting fromFile(File file) {
		if (!file.exists() || file.isDirectory()) {
			return null;
		}
		final GeneratorSettingFileType enm = GeneratorSettingFileType.parse(file);
		if (enm == null) {
			return null;
		}
		final WorkbookFileType workbookFileType = enm.getWorkbookFileType();
		if (workbookFileType.isWorkbook()) {
			try (Workbook wb = workbookFileType.createWorkBook(file, true)) {
				final TableDataGeneratorSetting setting = GeneratorSettingWorkbook.readWorkbook(wb);
				return setting;
			} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
				throw new RuntimeException(e);
			}
		} else if (workbookFileType.isJson() || workbookFileType.isYaml()) {
			try {
				final String text = FileUtils.readFileToString(file, Charset.forName("UTF8"));
				final JsonConverter jsonConverter = workbookFileType.createJsonConverter();
				TableDataGeneratorSetting setting = jsonConverter.fromJsonString(text, TableDataGeneratorSetting.class);
				return setting;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	protected void setTableDefaultValues(Table table, Dialect dialect, TableDataGeneratorSetting setting) {
		setting.setName(table.getName());
		setting.setNumberOfRows(100);
		String sql = dialect.createSqlBuilder().select().count().from()._fromSysDummy().toString();
		setting.setSetupSql(sql);
		setting.setFinalizeSql(sql);
	}

	protected void setColumnDefaultValues(Table table, Dialect dialect, TableDataGeneratorSetting setting) {
		int i = 0;
		for (final Column column : table.getColumns()) {
			ColumnDataGeneratorSetting colSetting = new ColumnDataGeneratorSetting();
			colSetting.setName(column.getName());
			colSetting.setDataType(column.getDataType());
			colSetting.setInsertExclude(column.isIdentity() || column.getSequenceName() != null);
			Object val = this.getColumnStartValue().apply(column);
			colSetting.setStartValue(val != null ? "" + val : null);
			val = this.getColumnMaxValue().apply(column);
			colSetting.setMaxValue(val != null ? "" + val : null);
			colSetting.setNextValue(this.getColumnNextValue().apply(column));
			setting.addColumn(colSetting, i++);
		}
	}

	protected void setQueryDefaultValue(Table table, Dialect dialect, TableDataGeneratorSetting setting) {
		final QueryDefinitionDataGeneratorSetting query = new QueryDefinitionDataGeneratorSetting();
		query.setGenerationGroup("Group1");
		query.setSelectSql(getDefaultSql(table, dialect));
		setting.addQueryDefinition(query, 0);
	}

	protected String getDefaultSql(Table table, Dialect dialect) {
		int i = 0;
		AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();

		sqlBuilder.select();
		sqlBuilder.appendIndent(+1);
		for (Column column : table.getColumns()) {
			sqlBuilder.lineBreak();
			sqlBuilder.comma(i > 0);
			Object val = column.getDataType().getDefaultValue();
			if (val == null) {
				continue;
			}
			if ("".equals(val)) {
				sqlBuilder._add("''");
			} else if (column.getDataType().isNumeric()) {
				sqlBuilder._add(Converters.getDefault().convertString(val));
			} else {
				sqlBuilder._add("'" + Converters.getDefault().convertString(val) + "'");
			}
			sqlBuilder.as().space();
			sqlBuilder.name(column.getName());
			i++;
		}
		sqlBuilder.appendIndent(+1);
		if (dialect.getSelectDummyTableName() != null) {
			sqlBuilder.lineBreak();
			sqlBuilder.from()._fromSysDummy();
		}
		return sqlBuilder.toString();
	}
}
