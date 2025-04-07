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

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
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
			setCellValueForHeader(row, j, "[1] Setup SQL\n(1 execution)", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[2] Start Value SQL\n(1 execution)", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[3] Number of Rows", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[4] Insert SQL\n([2]×[3] execution)", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
			setCellValueForHeader(row, j, "[5] Finalize SQL\n(1 execution)", null);
			row = ExcelUtils.getOrCreateRow(sheet, i++);
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
			setCellValueForHeader(row, j++, COLUMN_NAME, null);
			setCellValueForHeader(row, j++, DATA_TYPE, null);
			setCellValueForHeader(row, j++, GENERATION_GROUP, null);
			setCellValueForHeader(row, j++, MIN_VALUE, null);
			setCellValueForHeader(row, j++, MAX_VALUE, AVAILABLE_VAR + "\n====\n" + MIN_VALUE + " : "
					+ TableGeneratorSetting.MIN_KEY + ".[" + COLUMN_NAME + "]");
			setCellValueForHeader(row, j++, NEXT_VALUE,
					AVAILABLE_VAR + "\n====\n" + TableGeneratorSetting.INDEX_KEY + "\n" + MIN_VALUE + " : "
							+ TableGeneratorSetting.MIN_KEY + ".[" + COLUMN_NAME + "]\n" + MAX_VALUE + " : "
							+ TableGeneratorSetting.MAX_KEY + ".[" + COLUMN_NAME + "]\n" + PREVIOUS_VALUE + " : "
							+ TableGeneratorSetting.PREVIOUS_KEY + ".[" + COLUMN_NAME + "]");
			setCellValueForHeader(row, j++, VALUES, null);
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
			setCellValueForHeader(row, j++, GENERATION_GROUP, GENERATION_GROUP_NAME_COMMENT);
			setCellValueForHeader(row, j++, SELECT_SQL, SELECT_SQL_COMMENT);
			for (final Map.Entry<String, QueryGeneratorSetting> entry : setting.getQuerys().entrySet()) {
				j = 0;
				row = ExcelUtils.getOrCreateRow(sheet, i++);
				QueryGeneratorSetting col = entry.getValue();
				setCellValue(row, j++, col.getGenerationGroup());
				setCellValue(row, j++, col.getSelectSql(), true);
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
				setting.addQueryDefinition(def);
			}
		}
	};

	public void writeSheet(TableGeneratorSetting setting, Workbook wb) {

	}

	public void readFromSheet(Workbook wb, TableGeneratorSetting setting) {
	}

	private static final String GENERATION_GROUP_NAME_COMMENT = "If the name given here is set to the group name of the column sheet, the results of the SELECT SQL will be used.";
	private static final String SELECT_SQL_COMMENT = "Execute SQL with column names in AS and set to the group name of the column sheet, the results of the SELECT SQL will be used sequentially.";

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

	private static void setCellValueForHeader(Row row, int colIndex, Object value, String cellComment) {
		setCellValueForHeader(row, colIndex, value, cellComment, false, (sheet, cellStyle) -> {
			cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cellStyle.setAlignment(HorizontalAlignment.CENTER);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setFont(getFont(sheet.getWorkbook()));
		});
	}

	private static void setCellValueForHeader(Row row, int colIndex, Object value, String cellComment,
			boolean rowAutoHeight, BiConsumer<Sheet, CellStyle> cellStyleConsumer) {
		ExcelUtils.setCell(row, colIndex, cell -> {
			final Sheet sheet = cell.getSheet();
			final CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
			if (cellStyleConsumer != null) {
				cellStyleConsumer.accept(sheet, cellStyle);
			}
			cellStyle.setFont(getFont(sheet.getWorkbook()));
			sheet.autoSizeColumn(cell.getColumnIndex());
			// cellStyle.setWrapText(true);
			cell.setCellStyle(cellStyle);
			ExcelUtils.setCell(cell, value);
			sheet.autoSizeColumn(cell.getColumnIndex());
			if (rowAutoHeight) {
				setRowAutoHeight(row, value);
			}
			//
			if (cellComment != null) {
				ExcelUtils.setComment(cell, cellComment);
			}
		});
	}

	private static void setCellValue(Row row, int colIndex, Object value) {
		setCellValue(row, colIndex, value, false);
	}

	private static void setCellValue(Row row, int colIndex, Object value, boolean rowAutoHeight) {
		setCellValue(row, colIndex, value, rowAutoHeight, (sheet, cellStyle) -> {
			cellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//			cellStyle.setAlignment(HorizontalAlignment.CENTER);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setFont(getFont(sheet.getWorkbook()));
		});
	}

	private static void setCellValue(Row row, int colIndex, Object value, boolean rowAutoHeight,
			BiConsumer<Sheet, CellStyle> cellStyleConsumer) {
		ExcelUtils.setCell(row, colIndex, cell -> {
			final Sheet sheet = cell.getSheet();
			CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
			if (cellStyleConsumer != null) {
				cellStyleConsumer.accept(sheet, cellStyle);
			}
			cellStyle.setFont(getFont(sheet.getWorkbook()));
			if (!(value instanceof List)) {
				sheet.autoSizeColumn(cell.getColumnIndex());
				if (rowAutoHeight) {
					setRowAutoHeight(row, value);
				}
				cellStyle.setWrapText(true);
				cell.setCellStyle(cellStyle);
				ExcelUtils.setCell(cell, value);
				sheet.autoSizeColumn(cell.getColumnIndex());
			} else {
				@SuppressWarnings("unchecked")
				final List<Object> values = (List<Object>) value;
				if (CommonUtils.isEmpty(values)) {
					return;
				}
				ExcelUtils.setCell(cell, values.get(0));
				sheet.autoSizeColumn(cell.getColumnIndex());
				for (int i = 1; i < values.size(); i++) {
					cell = ExcelUtils.getOrCreateCell(row, i);
					cell.setCellStyle(cellStyle);
					cellStyle.setWrapText(true);
					ExcelUtils.setCell(cell, values.get(i));
					sheet.autoSizeColumn(cell.getColumnIndex());
				}
			}
		});
	}

	private static void setRowAutoHeight(Row row, Object value) {
		if (!(value instanceof String)) {
			return;
		}
		String[] args = String.class.cast(value).split("\n");
		row.setHeightInPoints(15 * args.length);
	}

	private static Font getFont(Workbook wb) {
		Font font = wb.createFont();
		// font.setFontName("Arial");
		font.setFontHeightInPoints((short) 11);
		font.setTypeOffset(Font.SS_NONE);
		return font;
	}
}
