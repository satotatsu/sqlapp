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

import static com.sqlapp.data.db.command.util.ExcelCommandUtils.setCellValue;
import static com.sqlapp.data.db.command.util.ExcelCommandUtils.setCellValueForHeader;

import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.FileGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.strategy.ValueSelectStrategy;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;

public enum GeneratorSettingWorkbook {
	Table() {
		@Override
		public void writeSheet(TableGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "Table Name", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[1] Setup SQL", "Run the initialization SQL once.");
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[2] Start Value SQL",
					"Execute the SQL statement for the starting values\\n once to select rows from the table.");
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[3] Row amplification factor", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[4] Insert SQL\n([2]×[3] rows insert.)", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[5] Finalize SQL", "Run the finalize SQL once.");
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			sheet.setColumnWidth(j, 256 * 30);
			i = 0;
			j = 1;
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getName());
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getSetupSql());
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getStartValueSql());
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getNumberOfRows());
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getInsertSql());
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getFinalizeSql());
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			int i = 0;
			int j = 1;
			Row row = sheet.getRow(i++);
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setName(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setSetupSql(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setStartValueSql(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setNumberOfRows(ExcelUtils.getCellValue(cell, Long.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setInsertSql(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setFinalizeSql(ExcelUtils.getCellValue(cell, String.class));
		}
	},
	Column() {
		@Override
		public void writeSheet(final TableGeneratorSetting setting, final Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			final int valuesMax = 30;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, COLUMN_NAME, null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, DATA_TYPE, null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, GENERATION_GROUP, null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, MIN_VALUE, null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, MAX_VALUE, AVAILABLE_VAR + "\n====\n" + MIN_VALUE + " : "
					+ TableGeneratorSetting.MIN_KEY + ".[" + COLUMN_NAME + "]", HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, NEXT_VALUE,
					AVAILABLE_VAR + "\n====\n" + TableGeneratorSetting.INDEX_KEY + "\n" + MIN_VALUE + " : "
							+ TableGeneratorSetting.MIN_KEY + ".[" + COLUMN_NAME + "]\n" + MAX_VALUE + " : "
							+ TableGeneratorSetting.MAX_KEY + ".[" + COLUMN_NAME + "]\n" + PREVIOUS_VALUE + " : "
							+ TableGeneratorSetting.PREVIOUS_KEY + ".[" + COLUMN_NAME + "]",
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, VALUES, null, HorizontalAlignment.CENTER);
			for (int k = 0; k < valuesMax; k++) {
				// valuesのために空の領域を作っておく
				setCellValueForHeader(row, j++, null, null);
			}
			//
			for (final Map.Entry<String, ColumnGeneratorSetting> entry : setting.getColumns().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				final ColumnGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getName());
				setCellValue(row, j++, col.getDataType());
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getMinValue());
				setCellValue(row, j++, col.getMaxValue());
				setCellValue(row, j++, col.getNextValue());
				setCellValue(row, j++, col.getValues());
				if (!CommonUtils.isEmpty(col.getValues())) {
					j = j + col.getValues().size();
				}
				for (int k = 0; k < valuesMax; k++) {
					// valuesのために空の領域を作っておく
					setCellValue(row, j++, null);
				}
			}
		}

		@Override
		public void readFromSheet(final Workbook wb, final TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			final int max = sheet.getLastRowNum();
			for (int i = 1; i <= max; i++) {
				Row row = sheet.getRow(i);
				int j = 0;
				final ColumnGeneratorSetting def = new ColumnGeneratorSetting();
				final String name = ExcelUtils.getCellValue(row, j++, String.class);
				if (CommonUtils.isBlank(name)) {
					return;
				}
				def.setName(name);
				def.setDataType(ExcelUtils.getCellValue(row, j++, DataType.class));
				def.setGenerationGroup(ExcelUtils.getCellValue(row, j++, String.class));
				def.setMinValue(ExcelUtils.getCellValue(row, j++, String.class));
				def.setMaxValue(ExcelUtils.getCellValue(row, j++, String.class));
				def.setNextValue(ExcelUtils.getCellValue(row, j++, String.class));
				while (j < row.getLastCellNum()) {
					Object val = ExcelUtils.getCellValue(row, j++);
					if (CommonUtils.isEmpty(val)) {
						break;
					}
					Object obj = Converters.getDefault().convertObject(val, def.getDataType().getDefaultClass());
					if (def.getValues() == null) {
						def.setValues(CommonUtils.list());
					}
					def.getValues().add(obj);
				}
				setting.addColumn(def);
			}
		}
	},
	Query() {
		@Override
		public void writeSheet(TableGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, GENERATION_GROUP, GENERATION_GROUP_NAME_COMMENT,
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, SELECT_SQL, SELECT_SQL_COMMENT, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, OFFSET, OFFSET_COMMENT, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, LIMIT, LIMIT_COMMENT, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, SELECTION_STRATEGY, SELECTION_STRATEGY_COMMENT, HorizontalAlignment.CENTER);
			for (final Map.Entry<String, QueryGeneratorSetting> entry : setting.getQuerys().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				QueryGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getSelectSql(), true);
				setCellValue(row, j++, col.getOffset());
				setCellValue(row, j++, col.getLimit());
				setCellValue(row, j++, col.getSelectionStrategy());
			}
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				final Row row = sheet.getRow(i);
				int j = 0;
				final QueryGeneratorSetting def = new QueryGeneratorSetting();
				def.setGenerationGroup(ExcelUtils.getCellValue(row, j++, String.class));
				if (CommonUtils.isBlank(def.getGenerationGroup())) {
					return;
				}
				def.setSelectSql(ExcelUtils.getCellValue(row, j++, String.class));
				Integer value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setOffset(value);
				}
				value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setLimit(value);
				}
				def.setSelectionStrategy(ValueSelectStrategy.parse(ExcelUtils.getCellValue(row, j++, String.class)));
				setting.addQueryDefinition(def);
			}
		}
	},
	File() {
		@Override
		public void writeSheet(TableGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, GENERATION_GROUP, GENERATION_GROUP_NAME_COMMENT,
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, DATA_SOURCE_EXPRESSION, DATA_SOURCE_EXPRESSION_COMMENT,
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, DATA_MAPPING_EXPRESSION, DATA_MAPPING_EXPRESSION_COMMENT,
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, OFFSET, OFFSET_COMMENT, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, LIMIT, LIMIT_COMMENT, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, SELECTION_STRATEGY, SELECTION_STRATEGY_COMMENT, HorizontalAlignment.CENTER);
			for (final Map.Entry<String, FileGeneratorSetting> entry : setting.getFiles().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				FileGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getDataSourceExpression(), true);
				setCellValue(row, j++, col.getDataMappingExpression(), true);
				setCellValue(row, j++, col.getOffset());
				setCellValue(row, j++, col.getLimit());
				setCellValue(row, j++, col.getSelectionStrategy());
			}
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				final Row row = sheet.getRow(i);
				int j = 0;
				final FileGeneratorSetting def = new FileGeneratorSetting();
				def.setGenerationGroup(ExcelUtils.getCellValue(row, j++, String.class));
				if (CommonUtils.isBlank(def.getGenerationGroup())) {
					return;
				}
				def.setDataSourceExpression(ExcelUtils.getCellValue(row, j++, String.class));
				def.setDataMappingExpression(ExcelUtils.getCellValue(row, j++, String.class));
				Integer value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setOffset(value);
				}
				value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setLimit(value);
				}
				def.setSelectionStrategy(ValueSelectStrategy.parse(ExcelUtils.getCellValue(row, j++, String.class)));
				setting.addFileDefinition(def);
			}
		}
	};

	public void writeSheet(TableGeneratorSetting setting, Workbook wb) {

	}

	public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
	}

	private static final String GENERATION_GROUP_NAME_COMMENT = "If the name given here is set to the group name of the column sheet, the results of the SELECT SQL will be used.";
	private static final String SELECT_SQL_COMMENT = "Execute SQL with column names in AS and set to the group name of the column sheet.";
	private static final String SELECTION_STRATEGY_COMMENT = "NEXT_VALUE OR RANDOM";
	private static final String DATA_SOURCE_EXPRESSION_COMMENT = "This is an MVEL expression that reads data sources such as files and returns an iterable of Map objects.";
	private static final String DATA_MAPPING_EXPRESSION_COMMENT = "This is an MVEL expression that creates a map that maps columns in the data source to columns in the table.";
	private static final String OFFSET_COMMENT = "offset for SELECT SQL.";
	private static final String LIMIT_COMMENT = "limit for SELECT SQL.";

	public static TableGeneratorSetting readWorkbook(Workbook wb) {
		TableGeneratorSetting setting = new TableGeneratorSetting();
		for (GeneratorSettingWorkbook enm : GeneratorSettingWorkbook.values()) {
			enm.readFromSheet(wb, setting);
		}
		setting.check();
		return setting;
	}

	private static String COLUMN_NAME = "Column Name";
	private static String DATA_TYPE = "Data Type";
	private static String GENERATION_GROUP = "Generation Group";
	private static String MIN_VALUE = "Min Value";
	private static String PREVIOUS_VALUE = "Previous Value";
	private static String MAX_VALUE = "Max Value";
	private static String NEXT_VALUE = "Next Value";
	private static String VALUES = "Values";
	private static String AVAILABLE_VAR = "Available Variables";
	private static String SELECT_SQL = "Select SQL";
	private static String DATA_SOURCE_EXPRESSION = "DataSource Expression";
	private static String DATA_MAPPING_EXPRESSION = "Data MappingExpression";
	private static String OFFSET = "Offset";
	private static String LIMIT = "Limit";
	private static String SELECTION_STRATEGY = "Selection Strategy";
}
