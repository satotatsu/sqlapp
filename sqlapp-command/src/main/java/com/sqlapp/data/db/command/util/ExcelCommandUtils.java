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

public class ExcelCommandUtils {

	public static CellStyle createCellStyleHeader(final Sheet sheet) {
		final CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
		cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
		cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		cellStyle.setAlignment(HorizontalAlignment.CENTER);
		cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
		cellStyle.setFont(getFont(sheet.getWorkbook()));
		Font font = getFont(sheet.getWorkbook());
		cellStyle.setFont(font);
		return cellStyle;
	}

	public static CellStyle createCellStyle(final Sheet sheet) {
		final CellStyle cellStyle = ExcelUtils.createCellStyle(sheet.getWorkbook(), BorderStyle.THIN);
		cellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
		cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//		cellStyle.setAlignment(HorizontalAlignment.CENTER);
		cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
		cellStyle.setFont(getFont(sheet.getWorkbook()));
		cellStyle.setWrapText(true);
		return cellStyle;
	}

	public static void setCellValue(Row row, int colIndex, final Object value, String cellComment,
			final CellStyle cellStyle) {
		ExcelUtils.setCell(row, colIndex, cellStyle, cell -> {
			ExcelUtils.setCell(cell, value);
			setRowAutoHeight(row, value);
			if (cellComment != null) {
				ExcelUtils.setComment(cell, cellComment);
			}
		});
	}

	public static boolean setRowAutoHeight(Row row, Object value) {
		if (!(value instanceof String)) {
			return false;
		}
		String[] args = String.class.cast(value).split("\n");
		if (args.length > 1) {
			row.setHeightInPoints(15 * (args.length + 1));
			return true;
		}
		return false;
	}

	private static Font getFont(Workbook wb) {
		Font font = wb.createFont();
		// font.setFontName("Arial");
		font.setFontHeightInPoints((short) 11);
		font.setTypeOffset(Font.SS_NONE);
		return font;
	}
}
