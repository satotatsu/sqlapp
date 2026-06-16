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

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

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
		public void writeSheet(TableGeneratorSetting setting, Locale locale, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, schemaName), null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getSchemaName());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, tableName), null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getName());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, startCountSql), getMessage(locale, startCountSqlComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getStartCountSql());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, initializeSql), getMessage(locale, initializeSqlComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getInitializeSql());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, startValueSql), getMessage(locale, startValueSqlComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getStartValueSql());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, dataSourceExpression),
					getMessage(locale, dataSourceExpressionComment));
			setCellValueForHeader(row, j + 1, getMessage(locale, columnMappingExpression),
					getMessage(locale, columnMappingExpressionComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getDataSourceExpression());
			setCellValue(row, j + 1, setting.getColumnMappingExpression());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, insertSql), null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getInsertSql());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, finalizeSql), getMessage(locale, finalizeSqlComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getFinalizeSql());
			//
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, getMessage(locale, finishCountSql),
					getMessage(locale, finishCountSqlComment));
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValue(row, j, setting.getFinishCountSql());

			sheet.setColumnWidth(j, 256 * 30);
			sheet.setColumnWidth(j + 1, 256 * 30);
			i = 0;
			j = 1;
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			int i = 1;
			int j = 0;
			Row row = sheet.getRow(i++);
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setSchemaName(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setName(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setStartCountSql(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setInitializeSql(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setStartValueSql(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setDataSourceExpression(ExcelUtils.getCellValue(cell, String.class));
			cell = ExcelUtils.getOrCreateCell(row, j + 1);
			setting.setColumnMappingExpression(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setInsertSql(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setFinalizeSql(ExcelUtils.getCellValue(cell, String.class));
			i++;
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setFinishCountSql(ExcelUtils.getCellValue(cell, String.class));
		}
	},
	Column() {
		@Override
		public void writeSheet(final TableGeneratorSetting setting, Locale locale, final Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			final int valuesMax = 30;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, getMessage(locale, columnName), null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, dataType), null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, generationGroup), null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, minValue), null, HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, maxValue), getMessage(locale, maxValueComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, nextValue), getMessage(locale, nextValueComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, values), null, HorizontalAlignment.CENTER);
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
			for (i = 0; i < 8; i++) {
				sheet.autoSizeColumn(i);
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
		public void writeSheet(TableGeneratorSetting setting, Locale locale, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, getMessage(locale, generationGroup), getMessage(locale, generationGroup),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, selectSql), getMessage(locale, selectSqlComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, columnMappingExpression),
					getMessage(locale, columnMappingExpressionComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, offset), getMessage(locale, offsetComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, limit), getMessage(locale, limitComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, selectionStrategy),
					getMessage(locale, selectionStrategyComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, selectionStrategyWeightExpression),
					getMessage(locale, selectionStrategyWeightExpressionComment), HorizontalAlignment.CENTER);

			for (final Map.Entry<String, QueryGeneratorSetting> entry : setting.getQuerys().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				QueryGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getSelectSql(), true);
				setCellValue(row, j++, col.getColumnMappingExpression(), true);
				setCellValue(row, j++, col.getOffset());
				setCellValue(row, j++, col.getLimit());
				setCellValue(row, j++, col.getSelectionStrategy());
				setCellValue(row, j++, col.getSelectionStrategyWeightExpression());
			}
			for (i = 0; i < 7; i++) {
				sheet.autoSizeColumn(i);
			}
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			if (sheet == null) {
				return;
			}
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				final Row row = sheet.getRow(i);
				int j = 0;
				final QueryGeneratorSetting def = new QueryGeneratorSetting();
				def.setGenerationGroup(ExcelUtils.getCellValue(row, j++, String.class));
				if (CommonUtils.isBlank(def.getGenerationGroup())) {
					return;
				}
				def.setSelectSql(ExcelUtils.getCellValue(row, j++, String.class));
				def.setColumnMappingExpression(ExcelUtils.getCellValue(row, j++, String.class));
				Integer value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setOffset(value);
				}
				value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setLimit(value);
				}
				def.setSelectionStrategy(ValueSelectStrategy.parse(ExcelUtils.getCellValue(row, j++, String.class)));
				def.setSelectionStrategyWeightExpression(ExcelUtils.getCellValue(row, j++, String.class));
				setting.addQueryDefinition(def);
			}
		}
	},
	File() {
		@Override
		public void writeSheet(TableGeneratorSetting setting, Locale locale, Workbook wb) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
			sheet.setDisplayGridlines(false);
			int i = 0;
			int j = 0;
			Row row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j++, getMessage(locale, generationGroup),
					getMessage(locale, generationGroupComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, fileDataSourceExpression),
					getMessage(locale, fileDataSourceExpressionComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, columnMappingExpression),
					getMessage(locale, columnMappingExpressionComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, offset), getMessage(locale, offsetComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, limit), getMessage(locale, limitComment),
					HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, selectionStrategy),
					getMessage(locale, selectionStrategyComment), HorizontalAlignment.CENTER);
			setCellValueForHeader(row, j++, getMessage(locale, selectionStrategyWeightExpression),
					getMessage(locale, selectionStrategyWeightExpressionComment), HorizontalAlignment.CENTER);

			for (final Map.Entry<String, FileGeneratorSetting> entry : setting.getFiles().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				FileGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getDataSourceExpression(), true);
				setCellValue(row, j++, col.getColumnMappingExpression(), true);
				setCellValue(row, j++, col.getOffset());
				setCellValue(row, j++, col.getLimit());
				setCellValue(row, j++, col.getSelectionStrategy());
				setCellValue(row, j++, col.getSelectionStrategyWeightExpression());
			}
		}

		@Override
		public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			if (sheet == null) {
				return;
			}
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				final Row row = sheet.getRow(i);
				int j = 0;
				final FileGeneratorSetting def = new FileGeneratorSetting();
				def.setGenerationGroup(ExcelUtils.getCellValue(row, j++, String.class));
				if (CommonUtils.isBlank(def.getGenerationGroup())) {
					return;
				}
				def.setDataSourceExpression(ExcelUtils.getCellValue(row, j++, String.class));
				def.setColumnMappingExpression(ExcelUtils.getCellValue(row, j++, String.class));
				Integer value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setOffset(value);
				}
				value = ExcelUtils.getCellValue(row, j++, Integer.class);
				if (value != null) {
					def.setLimit(value);
				}
				def.setSelectionStrategy(ValueSelectStrategy.parse(ExcelUtils.getCellValue(row, j++, String.class)));
				def.setSelectionStrategyWeightExpression(ExcelUtils.getCellValue(row, j++, String.class));
				setting.addFileDefinition(def);
			}
		}
	};

	public void writeSheet(TableGeneratorSetting setting, Locale locale, Workbook wb) {

	}

	public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
	}

	public static TableGeneratorSetting readWorkbook(Workbook wb) {
		TableGeneratorSetting setting = new TableGeneratorSetting();
		for (GeneratorSettingWorkbook enm : GeneratorSettingWorkbook.values()) {
			enm.readFromSheet(wb, setting);
		}
		setting.check();
		return setting;
	}

	public static ResourceBundle getResourceBundle(Locale locale) {
		String path = GeneratorSettingWorkbook.class.getPackageName();
		ResourceBundle resourceBundle = ResourceBundle.getBundle(path + ".messages", locale);
		return resourceBundle;
	}

	public static String getMessage(Locale locale, String key) {
		String value = getResourceBundle(locale).getString(key);
		return value;
	}

	private static final String startCountSql = "startCountSql";
	private static final String startCountSqlComment = "startCountSql";
	private static final String finishCountSql = "finishCountSql";
	private static final String finishCountSqlComment = "finishCountSqlComment";

	private static final String initializeSql = "initializeSql";
	private static final String initializeSqlComment = "initializeSqlComment";

	private static final String dataSourceExpression = "dataSourceExpression";
	private static final String dataSourceExpressionComment = "dataSourceExpressionComment";

	private static final String fileDataSourceExpression = "fileDataSourceExpression";
	private static final String fileDataSourceExpressionComment = "fileDataSourceExpressionComment";
	private static final String columnMappingExpression = "columnMappingExpression";
	private static final String columnMappingExpressionComment = "columnMappingExpressionComment";
	private static final String startValueSql = "startValueSql";
	private static final String startValueSqlComment = "startValueSqlComment";

	private static final String insertSql = "insertSql";
	private static final String finalizeSql = "finalizeSql";
	private static final String finalizeSqlComment = "finalizeSqlComment";

	private static final String schemaName = "schemaName";
	private static final String tableName = "tableName";
	private static final String columnName = "columnName";
	private static final String dataType = "dataType";
	private static final String generationGroup = "generationGroup";
	private static final String minValue = "minValue";
	private static final String maxValue = "maxValue";
	private static final String nextValue = "nextValue";
	private static final String nextValueComment = "nextValueComment";
	private static final String maxValueComment = "maxValueComment";
	private static final String values = "values";
	private static final String generationGroupComment = "generationGroupComment";
	private static final String selectSql = "selectSql";
	private static final String selectSqlComment = "selectSqlComment";
	private static final String offset = "offset";
	private static final String offsetComment = "offsetComment";
	private static final String limit = "limit";
	private static final String limitComment = "limitComment";
	private static final String selectionStrategy = "selectionStrategy";
	private static final String selectionStrategyComment = "selectionStrategyComment";
	private static final String selectionStrategyWeightExpression = "selectionStrategyWeightExpression";
	private static final String selectionStrategyWeightExpressionComment = "selectionStrategyWeightExpressionComment";

}
