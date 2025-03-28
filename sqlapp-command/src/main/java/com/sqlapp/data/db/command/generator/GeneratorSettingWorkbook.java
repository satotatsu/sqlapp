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

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.setting.ColumnDataGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryDefinitionDataGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableDataGeneratorSetting;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;

public enum GeneratorSettingWorkbook {
	Table() {
		@Override
		public void writeSheet(TableDataGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setColumnData2Sheet(sheetName, "Table Name", i++, setting.getName(), wb);
			setColumnData2Sheet(sheetName, "Number of Rows", i++, setting.getNumberOfRows(), wb);
			setColumnData2Sheet(sheetName, "Setup SQL", i++, setting.getSetupSql(), wb);
			setColumnData2Sheet(sheetName, "Finalize SQL", i++, setting.getFinalizeSql(), wb);
			Sheet sheet = wb.getSheet(sheetName);
			sheet.setDisplayGridlines(false);
		}

		@Override
		public void readFromSheet(Workbook wb, TableDataGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			int i = 0;
			int j = 1;
			Row row = sheet.getRow(i++);
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setName(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setNumberOfRows(ExcelUtils.getCellValue(cell, Long.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setSetupSql(ExcelUtils.getCellValue(cell, String.class));
			row = sheet.getRow(i++);
			cell = ExcelUtils.getOrCreateCell(row, j);
			setting.setFinalizeSql(ExcelUtils.getCellValue(cell, String.class));
		}
	},
	Column() {
		@Override
		public void writeSheet(TableDataGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setColumnData2Sheet(setting, sheetName, COLUMN_NAME, null, i++, wb, c -> c.getName());
			setColumnData2Sheet(setting, sheetName, DATA_TYPE, null, i++, wb, c -> c.getDataType());
			setColumnData2Sheet(setting, sheetName, GENERATION_GROUP, null, i++, wb, c -> c.getGenerationGroup());
			setColumnData2Sheet(setting, sheetName, INSERT_EXCLUDE, null, i++, wb, c -> c.isInsertExclude());
			setColumnData2Sheet(setting, sheetName, INSERT_SQL_EXPRESSION, null, i++, wb,
					c -> c.getInsertSqlExpression());
			setColumnData2Sheet(setting, sheetName, START_VALUE, null, i++, wb, c -> c.getStartValue());
			setColumnData2Sheet(
					setting, sheetName, MAX_VALUE, AVAILABLE_VAR + "\n====\n" + START_VALUE + " : "
							+ TableDataGeneratorSetting.START_KEY + ".[" + COLUMN_NAME + "]",
					i++, wb, c -> c.getMaxValue());
			setColumnData2Sheet(setting, sheetName, NEXT_VALUE,
					AVAILABLE_VAR + "\n====\n" + TableDataGeneratorSetting.INDEX_KEY + "\n" + START_VALUE + " : "
							+ TableDataGeneratorSetting.START_KEY + ".[" + COLUMN_NAME + "]\n" + MAX_VALUE + " : "
							+ TableDataGeneratorSetting.MAX_KEY + ".[" + COLUMN_NAME + "]\n" + PREVIOUS_VALUE + " : "
							+ TableDataGeneratorSetting.PREVIOUS_KEY + ".[" + COLUMN_NAME + "]",
					i++, wb, c -> c.getNextValue());
			setColumnData2Sheet(setting, sheetName, VALUES, null, i++, wb, c -> c.getValues());
			Sheet sheet = wb.getSheet(sheetName);
			sheet.setDisplayGridlines(false);
		}

		@Override
		public void readFromSheet(Workbook wb, TableDataGeneratorSetting setting) {
			final String sheetName = this.name();
			final Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			int i = 0;
			Row row = sheet.getRow(i++);
			for (int j = 1; j < row.getLastCellNum(); j++) {
				ColumnDataGeneratorSetting def = new ColumnDataGeneratorSetting();
				Cell cell = ExcelUtils.getOrCreateCell(row, j);
				def.setColString(ExcelUtils.convertNumToColString(cell));
				def.setName(ExcelUtils.getCellValue(cell, String.class));
				setting.addColumn(def, j);
			}
			setColumnSetting(sheet.getRow(i++), setting, DataType.class, (c, val) -> c.setDataType(val));
			setColumnSetting(sheet.getRow(i++), setting, String.class, (c, val) -> c.setGenerationGroup(val));
			setColumnSetting(sheet.getRow(i++), setting, Boolean.class, (c, val) -> c.setInsertExclude(val));
			setColumnSetting(sheet.getRow(i++), setting, String.class, (c, val) -> c.setInsertSqlExpression(val));
			setColumnSetting(sheet.getRow(i++), setting, String.class, (c, val) -> c.setStartValue(val));
			setColumnSetting(sheet.getRow(i++), setting, String.class, (c, val) -> c.setMaxValue(val));
			setColumnSetting(sheet.getRow(i++), setting, String.class, (c, val) -> c.setNextValue(val));
			while (true) {
				row = sheet.getRow(i++);
				if (row == null) {
					break;
				}
				setColumnSetting(row, setting, (c, val) -> {
					if (!CommonUtils.isEmpty(val)) {
						Object obj = Converters.getDefault().convertObject(val, c.getDataType().getDefaultClass());
						if (c.getValues() == null) {
							c.setValues(CommonUtils.list());
						}
						c.getValues().add(obj);
					}
				});
			}
		}
	},
	QueryDefinition() {
		@Override
		public void writeSheet(TableDataGeneratorSetting setting, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setQueryData2Sheet(setting, sheetName, GENERATION_GROUP, GENERATION_GROUP_NAME_COMMENT, i++, wb,
					c -> c.getGenerationGroup());
			setQueryData2Sheet(setting, sheetName, SELECT_SQL, SELECT_SQL_COMMENT, i++, wb, c -> c.getSelectSql());
			Sheet sheet = wb.getSheet(sheetName);
			sheet.setDisplayGridlines(false);
		}

		@Override
		public void readFromSheet(Workbook wb, TableDataGeneratorSetting setting) {
			final String sheetName = this.name();
			Sheet sheet = ExcelUtils.getSheet(wb, sheetName);
			int i = 0;
			Row row = sheet.getRow(i++);
			if (row == null) {
				return;
			}
			int lastCellNum = row.getLastCellNum();
			for (int j = 1; j < lastCellNum; j++) {
				Cell cell = row.getCell(j);
				final QueryDefinitionDataGeneratorSetting def = new QueryDefinitionDataGeneratorSetting();
				def.setColString(ExcelUtils.convertNumToColString(cell));
				def.setGenerationGroup(ExcelUtils.getCellValue(cell, String.class));
				//
				row = sheet.getRow(i++);
				if (row == null) {
					return;
				}
				cell = row.getCell(j);
				String val = (String) ExcelUtils.getCellValue(cell);
				def.setSelectSql(val);
				setting.addQueryDefinition(def, j);
			}
		}
	};

	public void writeSheet(TableDataGeneratorSetting setting, Workbook wb) {

	}

	public void readFromSheet(Workbook wb, TableDataGeneratorSetting setting) {
	}

	private static final String GENERATION_GROUP_NAME_COMMENT = "If the name given here is set to the group name of the column sheet, the results of the SELECT SQL will be used.";
	private static final String SELECT_SQL_COMMENT = "Execute SQL with column names in AS and set to the group name of the column sheet, the results of the SELECT SQL will be used sequentially.";

	public static TableDataGeneratorSetting readWorkbook(Workbook wb) {
		TableDataGeneratorSetting setting = new TableDataGeneratorSetting();
		for (GeneratorSettingWorkbook enm : GeneratorSettingWorkbook.values()) {
			enm.readFromSheet(wb, setting);
		}
		setting.check();
		return setting;
	}

	private static String COLUMN_NAME = "Column Name";
	private static String DATA_TYPE = "Data Type";
	private static String GENERATION_GROUP = "Generation Group";
	private static String INSERT_EXCLUDE = "Insert Exclude";
	private static String INSERT_SQL_EXPRESSION = "Insert SQL Expression";
	private static String START_VALUE = "Start Value";
	private static String PREVIOUS_VALUE = "Previous Value";
	private static String MAX_VALUE = "Max Value";
	private static String NEXT_VALUE = "Next Value";
	private static String VALUES = "Values";
	private static String AVAILABLE_VAR = "Available Variables";

	private static String SELECT_SQL = "SELECT SQL";

	private static <T> void setColumnSetting(Row row, TableDataGeneratorSetting setting, Class<T> clazz,
			BiConsumer<ColumnDataGeneratorSetting, T> cons) {
		for (int j = 1; j < row.getLastCellNum(); j++) {
			ColumnDataGeneratorSetting col = setting.getColumnIndexs().get(j);
			if (col == null) {
				continue;
			}
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			// String position = ExcelUtils.getCellPositionAsString(cell);
			T val = ExcelUtils.getCellValue(cell, clazz);
			cons.accept(col, val);
		}
	}

	private static void setColumnSetting(Row row, TableDataGeneratorSetting setting,
			BiConsumer<ColumnDataGeneratorSetting, Object> cons) {
		for (int j = 1; j < row.getLastCellNum(); j++) {
			final ColumnDataGeneratorSetting col = setting.getColumnIndexs().get(j);
			if (col == null) {
				continue;
			}
			final Cell cell = row.getCell(j);
			if (cell != null) {
				final Object val = ExcelUtils.getCellValue(cell);
				if (val != null) {
					cons.accept(col, val);
				}
			}
		}
	}

	private static void setColumnData2Sheet(String sheetName, String header, String cellComment, int rowNo,
			Workbook wb) {
		setColumnData2Sheet(sheetName, header, cellComment, rowNo, 0, wb);
	}

	private static void setColumnData2Sheet(String sheetName, Object value, String cellComment, int rowNo, int colIndex,
			Workbook wb) {
		ExcelUtils.setCell(wb, sheetName, rowNo, colIndex, cell -> {
			final Sheet sheet = cell.getSheet();
			CellStyle cellStyle = ExcelUtils.createCellStyle(wb, BorderStyle.HAIR, IndexedColors.WHITE);
			cellStyle.setFont(getFont(wb));
			sheet.autoSizeColumn(cell.getColumnIndex());
			ExcelUtils.setCell(cell, value);
			cellStyle.setWrapText(true);
			cell.setCellStyle(cellStyle);
			//
			if (cellComment != null) {
				ExcelUtils.setComment(cell, cellComment);
			}
		});
	}

	private static void setColumnData2Sheet(String sheetName, String header, int rowNo, Object value, Workbook wb) {
		Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		int j = 0;
		Cell cell = ExcelUtils.getOrCreateCell(row, j++);
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, BorderStyle.HAIR, IndexedColors.WHITE);
		cellStyle.setFont(getFont(wb));
		ExcelUtils.setCell(cell, header);
		sheet.autoSizeColumn(cell.getColumnIndex());
		cellStyle.setWrapText(true);
		cell.setCellStyle(cellStyle);
		//
		cell = ExcelUtils.getOrCreateCell(row, j++);
		cell.setCellStyle(cellStyle);
		ExcelUtils.setCell(cell, value);
	}

	private static void setColumnData2Sheet(TableDataGeneratorSetting setting, String sheetName, String header,
			String cellComment, int rowNo, Workbook wb, Function<ColumnDataGeneratorSetting, Object> func) {
		setColumnData2Sheet(sheetName, header, cellComment, rowNo, wb);
		Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		int j = 1;
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, null, IndexedColors.AQUA);
		cellStyle.setFont(getFont(wb));
		for (Map.Entry<String, ColumnDataGeneratorSetting> entry : setting.getColumns().entrySet()) {
			ColumnDataGeneratorSetting col = entry.getValue();
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			cell.setCellComment(null);
			cell.setCellStyle(cellStyle);
			cellStyle.setWrapText(true);
			Object obj = func.apply(col);
			if (!(obj instanceof List)) {
				ExcelUtils.setCell(cell, obj);
			} else {
				@SuppressWarnings("unchecked")
				final List<Object> values = (List<Object>) obj;
				if (CommonUtils.isEmpty(values)) {
					return;
				}
				ExcelUtils.setCell(cell, values.get(0));
				for (int i = 1; i < values.size(); i++) {
					row = ExcelUtils.getOrCreateRow(sheet, rowNo + i);
					cell = ExcelUtils.getOrCreateCell(row, j);
					cell.setCellStyle(cellStyle);
					cellStyle.setWrapText(true);
					ExcelUtils.setCell(cell, values.get(i));
				}
			}
			sheet.autoSizeColumn(cell.getColumnIndex());
			j++;
		}
	}

	private static void setQueryData2Sheet(TableDataGeneratorSetting setting, String sheetName, String header,
			String cellComment, int rowNo, Workbook wb, Function<QueryDefinitionDataGeneratorSetting, Object> func) {
		setColumnData2Sheet(sheetName, header, cellComment, rowNo, wb);
		Sheet sheet = ExcelUtils.getOrCreateSheet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		int j = 1;
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, null, IndexedColors.AQUA);
		cellStyle.setFont(getFont(wb));
		for (Map.Entry<String, QueryDefinitionDataGeneratorSetting> entry : setting.getQueryDefinitions().entrySet()) {
			QueryDefinitionDataGeneratorSetting col = entry.getValue();
			Cell cell = ExcelUtils.getOrCreateCell(row, j);
			cell.setCellComment(null);
			cell.setCellStyle(cellStyle);
			cellStyle.setWrapText(true);
			Object obj = func.apply(col);
			ExcelUtils.setCell(cell, obj);
			sheet.autoSizeColumn(cell.getColumnIndex());
			j++;
		}
	}

	private static Font getFont(Workbook wb) {
		Font font = wb.createFont();
		// font.setFontName("Arial");
		font.setFontHeightInPoints((short) 11);
		font.setTypeOffset(Font.SS_NONE);
		return font;
	}
}
