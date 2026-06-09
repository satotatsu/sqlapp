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

package com.sqlapp.data.db.command.util;

import java.util.List;
import java.util.function.BiConsumer;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;

public class ExcelCommandUtils {

	public static void setCellValueForHeader(Row row, int colIndex, Object value, String cellComment) {
		setCellValueForHeader(row, colIndex, value, cellComment, false, (sheet, cellStyle) -> {
			cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cellStyle.setAlignment(HorizontalAlignment.LEFT);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setFont(getFont(sheet.getWorkbook()));
		});
	}

	public static void setCellValueForHeader(Row row, int colIndex, Object value, String cellComment,
			HorizontalAlignment horizontalAlignment) {
		setCellValueForHeader(row, colIndex, value, cellComment, false, (sheet, cellStyle) -> {
			cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cellStyle.setAlignment(horizontalAlignment);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setFont(getFont(sheet.getWorkbook()));
		});
	}

	public static void setCellValueForHeader(Row row, int colIndex, Object value, String cellComment,
			boolean rowAutoHeight, BiConsumer<Sheet, CellStyle> cellStyleConsumer) {
		ExcelUtils.setCell(row, colIndex, cell -> {
			final Sheet sheet = cell.getSheet();
			final CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
			if (cellStyleConsumer != null) {
				cellStyleConsumer.accept(sheet, cellStyle);
			}
			Font font = getFont(sheet.getWorkbook());
			cellStyle.setFont(font);
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

	public static void setCellValue(Row row, int colIndex, Object value) {
		setCellValue(row, colIndex, value, false);
	}

	public static void setCellValue(Row row, int colIndex, Object value, boolean rowAutoHeight) {
		setCellValue(row, colIndex, value, rowAutoHeight, (sheet, cellStyle) -> {
			cellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//			cellStyle.setAlignment(HorizontalAlignment.CENTER);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setFont(getFont(sheet.getWorkbook()));
		});
	}

	public static void setCellValue(Row row, int colIndex, Object value, boolean rowAutoHeight,
			BiConsumer<Sheet, CellStyle> cellStyleConsumer) {
		ExcelUtils.setCell(row, colIndex, cell -> {
			final Sheet sheet = cell.getSheet();
			CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
			if (cellStyleConsumer != null) {
				cellStyleConsumer.accept(sheet, cellStyle);
			}
			cellStyle.setFont(getFont(sheet.getWorkbook()));
			if (!(value instanceof List)) {
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

	public static void setRowAutoHeight(Row row, Object value) {
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
