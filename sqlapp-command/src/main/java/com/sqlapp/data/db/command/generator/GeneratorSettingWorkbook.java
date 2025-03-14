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

import java.time.LocalDate;
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
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;

public enum GeneratorSettingWorkbook {
	Table() {
		@Override
		public void writeSheet(Table table, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setColumnData2Sheet(sheetName, "Table Name", i++, table.getName(), wb);
			setColumnData2Sheet(sheetName, "Number of Rows", i++, 100, wb);
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
		}
	},
	Column() {
		@Override
		public void writeSheet(Table table, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setColumnData2Sheet(table, sheetName, COLUMN_NAME, null, i++, wb, c -> c.getName());
			setColumnData2Sheet(table, sheetName, DATA_TYPE, null, i++, wb, c -> c.getDataType());
			setColumnData2Sheet(table, sheetName, GENERATION_GROUP, null, i++, wb, c -> "");
			setColumnData2Sheet(table, sheetName, INSERT_EXCLUDE, null, i++, wb,
					c -> c.isIdentity() || c.getSequenceName() != null);
			setColumnData2Sheet(table, sheetName, START_VALUE, null, i++, wb, c -> getColumnStartValue(c));
			setColumnData2Sheet(
					table, sheetName, MAX_VALUE, AVAILABLE_VAR + "\n====\n" + START_VALUE + " : "
							+ TableDataGeneratorSetting.START_KEY + ".[" + COLUMN_NAME + "]",
					i++, wb, c -> getColumnMaxValue(c));
			setColumnData2Sheet(table, sheetName, NEXT_VALUE,
					AVAILABLE_VAR + "\n====\n" + TableDataGeneratorSetting.INDEX_KEY + "\n" + START_VALUE + " : "
							+ TableDataGeneratorSetting.START_KEY + ".[" + COLUMN_NAME + "]\n" + MAX_VALUE + " : "
							+ TableDataGeneratorSetting.MAX_KEY + ".[" + COLUMN_NAME + "]\n" + PREVIOUS_VALUE + " : "
							+ TableDataGeneratorSetting.PREVIOUS_KEY + ".[" + COLUMN_NAME + "]",
					i++, wb, c -> getColumnNextValue(c));
			setColumnData2Sheet(table, sheetName, VALUES, null, i++, wb, c -> null);
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
		public void writeSheet(Table table, Workbook wb) {
			final String sheetName = this.name();
			int i = 0;
			setColumnData2Sheet(sheetName, "Generation Group", null, i++, wb);
			setColumnData2Sheet(sheetName, "SELECT　SQL", null, i++, wb);
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

	public void writeSheet(Table table, Workbook wb) {

	}

	public void readFromSheet(Workbook wb, TableDataGeneratorSetting setting) {
	}

	public static TableDataGeneratorSetting readWorkbook(Workbook wb) {
		TableDataGeneratorSetting setting = new TableDataGeneratorSetting();
		for (GeneratorSettingWorkbook enm : GeneratorSettingWorkbook.values()) {
			enm.readFromSheet(wb, setting);
		}
		setting.check();
		return setting;
	}

	private static Object getColumnStartValue(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return "true";
		}
		if (column.getDataType() == DataType.DOUBLE) {
			return "1.0d";
		}
		if (column.getDataType() == DataType.FLOAT) {
			return "1.0f";
		}
		if (column.getDataType().isNumeric()) {
			return Converters.getNewBooleanTrueInstance().convertObject(1, column.getDataType().getDefaultClass());
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			LocalDate dt = LocalDate.now();
			return "LocalDateTime.of(" + dt.getYear() + "," + dt.getMonthValue() + ",1,0,0,0)";
		}
		if (column.getDataType() == DataType.TIME) {
			return "LocalTime.of(0,0,0)";
		}
		if (column.getDataType() == DataType.DATE) {
			LocalDate dt = LocalDate.now();
			return "LocalDate.of(" + dt.getYear() + "," + dt.getMonthValue() + ",1)";
		}
		if (column.getDataType().isCharacter()) {
			return getDefaultCharacterExpression(column);
		}
		if (column.getDataType() == DataType.UUID) {
			return getDefaultUUIDExpression(column);
		}
		return null;
	}

	private static String getDefaultCharacterExpression(Column column) {
		if (column.getLength() == null) {
			return "nextAlphaNumeric(10)";
		}
		return "nextAlphaNumeric( " + column.getLength() + " )";
	}

	private static String getDefaultUUIDExpression(Column column) {
		return "java.util.UUID.randomUUID()";
	}

	private static String COLUMN_NAME = "Column Name";
	private static String DATA_TYPE = "Data Type";
	private static String GENERATION_GROUP = "Generation Group";
	private static String INSERT_EXCLUDE = "Insert Exclude";
	private static String START_VALUE = "Start Value";
	private static String PREVIOUS_VALUE = "Previous Value";
	private static String MAX_VALUE = "Max Value";
	private static String NEXT_VALUE = "Next Value";
	private static String VALUES = "Values";
	private static String AVAILABLE_VAR = "Available Variables";

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

	private static Object getColumnMaxValue(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return null;
		}
		if (column.getDataType() == DataType.NUMERIC || column.getDataType() == DataType.DECIMAL) {
			return calculateDecimalMaxValue(column);
		}
		if (column.getDataType().isNumeric()) {
			if (column.getDataType().getMaxValue() != null) {
				return column.getDataType().getMaxValue();
			}
			return null;
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			return "addMonths(" + TableDataGeneratorSetting.START_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.TIME) {
			return null;
		}
		if (column.getDataType() == DataType.DATE) {
			return "addMonths(" + TableDataGeneratorSetting.START_KEY + "." + column.getName() + ",1)";
		}
		return null;
	}

	private static long calculateDecimalMaxValue(Column column) {
		Long length = column.getLength();
		Integer scale = column.getScale();
		long len;
		if (scale == null) {
			len = length.longValue();
		} else {
			len = length.longValue() - scale.intValue();
		}
		return (long) Math.pow(10, len);
	}

	private static Object getColumnNextValue(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return "!" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName();
		}
		if (column.getDataType() == DataType.DOUBLE) {
			return "nextDouble(0.0d, 1000.0d)";
		}
		if (column.getDataType() == DataType.FLOAT) {
			return "nextDouble(0.0f, 1000.0f)";
		}
		if (column.getDataType().isNumeric()) {
			return "" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + " + 1";
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			return "addMilliSeconds(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.TIME) {
			return "addSeconds(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.DATE) {
			return "addDays(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType().isCharacter()) {
			return getDefaultCharacterExpression(column);
		}
		if (column.getDataType() == DataType.UUID) {
			return getDefaultUUIDExpression(column);
		}
		return null;
	}

	private static void setColumnData2Sheet(String sheetName, String header, String cellComment, int rowNo,
			Workbook wb) {
		int j = 0;
		Sheet sheet = ExcelUtils.getOrCreateSeet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		Cell cell = ExcelUtils.getOrCreateCell(row, j++);
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, BorderStyle.HAIR, IndexedColors.AQUA);
		cellStyle.setFont(getFont(wb));
		cell.setCellStyle(cellStyle);
		ExcelUtils.setCell(wb, cell, header);
		sheet.autoSizeColumn(cell.getColumnIndex());
		cellStyle.setWrapText(true);
		ExcelUtils.setCell(wb, cell, header);
		if (cellComment != null) {
			ExcelUtils.setComment(cell, cellComment);
		}
	}

	private static void setColumnData2Sheet(String sheetName, String header, int rowNo, Object value, Workbook wb) {
		Sheet sheet = ExcelUtils.getOrCreateSeet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		int j = 0;
		Cell cell = ExcelUtils.getOrCreateCell(row, j++);
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, BorderStyle.HAIR, IndexedColors.AQUA);
		cellStyle.setFont(getFont(wb));
		cell.setCellStyle(cellStyle);
		ExcelUtils.setCell(wb, cell, header);
		sheet.autoSizeColumn(cell.getColumnIndex());
		cellStyle.setWrapText(true);
		cell = ExcelUtils.getOrCreateCell(row, j++);
		cell.setCellStyle(cellStyle);
		ExcelUtils.setCell(wb, cell, value);
	}

	private static void setColumnData2Sheet(Table table, String sheetName, String header, String cellComment, int rowNo,
			Workbook wb, Function<Column, Object> func) {
		setColumnData2Sheet(sheetName, header, cellComment, rowNo, wb);
		Sheet sheet = ExcelUtils.getOrCreateSeet(wb, sheetName);
		Row row = ExcelUtils.getOrCreateRow(sheet, rowNo);
		int j = 1;
		CellStyle cellStyle = ExcelUtils.createCellStyle(wb, null, IndexedColors.AQUA);
		cellStyle.setFont(getFont(wb));
		for (Column column : table.getColumns()) {
			Cell cell = ExcelUtils.getOrCreateCell(row, j++);
			cell.setCellComment(null);
			cell.setCellStyle(cellStyle);
			cellStyle.setWrapText(true);
			ExcelUtils.setCell(wb, cell, func.apply(column));
			sheet.autoSizeColumn(cell.getColumnIndex());
		}
	}

	private static Font getFont(Workbook wb) {
		Font font = wb.createFont();
		// font.setFontName("Arial");
		font.setFontHeightInPoints((short) 16);
		font.setTypeOffset(Font.SS_SUPER);
		return font;
	}
}
