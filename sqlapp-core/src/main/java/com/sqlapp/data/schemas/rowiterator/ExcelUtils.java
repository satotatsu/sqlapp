/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas.rowiterator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.StringUtils;

/**
 * Excel操作用のユーティリティ
 * 
 * @author tatsuo satoh
 * 
 */
public class ExcelUtils {

	public static int getCellWidthByType(final Column column) {
		if (column.getDataType().isNumeric()) {
			if (column.getDataType().isFixedSize()) {
				if (column.getLength() != null) {
					return column.getLength().intValue();
				} else {
					return 10;
				}
			} else {
				switch (column.getDataType()) {
				case BIT:
					return 1;
				case TINYINT:
				case UTINYINT:
					return 3;
				case SMALLINT:
				case USMALLINT:
					return 5;
				case MEDIUMINT:
				case UMEDIUMINT:
					return 8;
				case INT:
				case UINT:
					return 10;
				case BIGINT:
				case UBIGINT:
					return 19;
				default:
					return 10;
				}
			}
		} else if (column.getDataType().isBoolean()) {
			return 5;
		} else if (column.getDataType().isDateTime()) {
			return 10;
		}
		return 10;
	}

	public static int getCellWidth(final Column column) {
		final int width = getCellWidthByType(column);
		final int width2 = getCellWidth(column.getName());
		return Math.max(width, width2);
	}

	/**
	 * セルの幅を取得します。
	 * 
	 * @param value
	 * @return セルの幅
	 */
	public static int getCellWidth(final String value) {
		return StringUtils.getDisplayWidth(value);
	}

	public static String getStringCellValue(final Cell cell) {
		if (cell == null) {
			return null;
		}
		return cell.getStringCellValue();
	}

	public static Object getCellValue(final Cell cell) {
		if (cell == null) {
			return null;
		}
		switch (cell.getCellType()) {
		case BLANK:
			return null;
		case _NONE:
			return null;
		case BOOLEAN:
			return cell.getBooleanCellValue();
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return cell.getDateCellValue();
			} else {
				return cell.getNumericCellValue();
			}
		case STRING:
			return cell.getStringCellValue();
		case FORMULA:
			return getFormulaCellValue(cell);
		default:
			;
		}
		return null;
	}

	/**
	 * 指定した型でセルの値を取得します
	 * 
	 * @param <T>   取得したい型
	 * @param cell  セル
	 * @param clazz 取得したい型のクラス
	 * @return 指定した型のセルの値
	 */
	public static <T> T getCellValue(final Cell cell, Class<T> clazz) {
		Object obj = getCellValue(cell);
		return Converters.getDefault().convertObject(obj, clazz);
	}

	private static Object getFormulaCellValue(final Cell cell) {
		final CellValue value = getEvaluatedCellValue(cell);
		switch (value.getCellType()) {
		case BLANK:
			return null;
		case _NONE:
			return null;
		case BOOLEAN:
			return cell.getBooleanCellValue();
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return DateUtil.getJavaDate(value.getNumberValue());
			} else {
				return value.getNumberValue();
			}
		case STRING:
			return cell.getStringCellValue();
		default:
			;
		}
		return null;
	}

	private static CellValue getEvaluatedCellValue(final Cell cell) {
		final Workbook book = cell.getSheet().getWorkbook();
		final CreationHelper helper = book.getCreationHelper();
		final FormulaEvaluator evaluator = helper.createFormulaEvaluator();
		final CellValue value = evaluator.evaluate(cell);
		return value;
	}

	public static void setColumnType(final Cell cell, final Column column) {
		switch (cell.getCellType()) {
		case BLANK:
			if (column.getDataType() != null && column.getDataType() != DataType.NVARCHAR) {
				return;
			}
			column.setDataType(DataType.NVARCHAR);
			column.setLength(DialectUtils.getDefaultTypeLength(null));
			break;
		case BOOLEAN:
			if (column.getDataType() != null && column.getDataType() != DataType.BOOLEAN) {
				return;
			}
			column.setDataType(DataType.BOOLEAN);
			break;
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				if (column.getDataType() != null && column.getDataType() != DataType.DATETIME) {
					return;
				}
				column.setDataType(DataType.DATETIME);
			} else {
				if (hasDecimalPoint(cell)) {
					column.setDataType(DataType.DOUBLE);
				} else {
					if (column.getDataType() != DataType.DOUBLE) {
						column.setDataType(DataType.BIGINT);
					}
				}
			}
			break;
		case STRING:
			column.setDataType(DataType.NVARCHAR);
			column.setLength(DialectUtils.getDefaultTypeLength(null));
		default:
			;
		}
	}

	private static boolean hasDecimalPoint(final Cell cell) {
		final double dbValue = cell.getNumericCellValue();
		final double dbValue2 = Math.round(dbValue);
		return dbValue != dbValue2;
	}

	public static Sheet getFirstOrCreateSeet(final Workbook workbook, final String sheetName) {
		Sheet sheet = workbook.getSheet(sheetName);
		if (sheet == null) {
			if (workbook.getNumberOfSheets() > 0) {
				sheet = workbook.getSheetAt(0);
			}
			if (sheet == null) {
				return workbook.createSheet(sheetName);
			}
		}
		return sheet;
	}

	public static Sheet getOrCreateSeet(final Workbook workbook, final String sheetName) {
		final Sheet sheet = getSheet(workbook, sheetName);
		if (sheet == null) {
			return workbook.createSheet(sheetName);
		}
		return sheet;
	}

	public static Sheet getSheet(final Workbook workbook, final String sheetName) {
		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheet = workbook.getSheetAt(i);
			if (sheet.getSheetName().equalsIgnoreCase(sheetName)) {
				return sheet;
			}
		}
		return null;
	}

	public static Row getOrCreateRow(final Sheet sheet, final int rownum) {
		Row row = sheet.getRow(rownum);
		if (row == null) {
			row = sheet.createRow(rownum);
		}
		return row;
	}

	public static Cell getOrCreateCell(final Row row, final int cellnum) {
		Cell cell = row.getCell(cellnum);
		if (cell == null) {
			cell = row.createCell(cellnum);
		}
		return cell;
	}

	/**
	 * 列番号を列文字に変換します
	 * 
	 * @param columnIndex 列番号
	 * @return 列文字
	 */
	public static String convertNumToColString(int columnIndex) {
		return CellReference.convertNumToColString(columnIndex);
	}

	/**
	 * セルを列文字に変換します
	 * 
	 * @param cell セル
	 * @return 列文字
	 */
	public static String convertNumToColString(Cell cell) {
		return CellReference.convertNumToColString(cell.getColumnIndex());
	}

	/**
	 * セルの位置をA1形式で取得します
	 * 
	 * @param cell セル
	 * @return 列文字
	 */
	public static String getCellPositionAsString(Cell cell) {
		return new CellReference(cell).formatAsString();
	}

	/**
	 * ワークブックを読み込んで処理を行います
	 * 
	 * @param file            読み込むファイル
	 * @param workbookHandler ワークブックハンドラー
	 * @throws FileNotFoundException
	 */
	public static void readWorkbook(final File file, final WorkbookHandler workbookHandler)
			throws FileNotFoundException {
		FileInputStream in = null;
		Workbook workbook = null;
		try {
			if (!file.exists()) {
				throw new FileNotFoundException(file.getAbsolutePath());
			}
			in = new FileInputStream(file);
			workbook = WorkbookFactory.create(in);
			workbookHandler.handle(workbook);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			FileUtils.close(in);
		}
	}

	/**
	 * ワークブックをファイルに書き込みます。
	 * 
	 * @param workbook
	 * @param file
	 * @throws FileNotFoundException, IOException
	 */
	public static void writeWorkbook(final Workbook workbook, final File file)
			throws FileNotFoundException, IOException {
		try (FileOutputStream os = new FileOutputStream(file); BufferedOutputStream bs = new BufferedOutputStream(os)) {
			workbook.write(bs);
			bs.flush();
		}
	}

	/**
	 * ワークブック処理用のインタフェース
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	@FunctionalInterface
	public static interface WorkbookHandler {
		void handle(Workbook workbook);
	}

	/**
	 * セルに値を設定します
	 * 
	 * @param converters コンバーター
	 * @param workbook   Workbook
	 * @param cell       セル
	 * @param obj        設定する値
	 */
	public static void setCell(final Converters converters, final Workbook workbook, final Cell cell,
			final Object obj) {
		if (obj instanceof Boolean) {
			cell.setCellValue((Boolean) obj);
		} else if (obj instanceof Double) {
			cell.setCellValue((Double) obj);
		} else if (obj instanceof LocalDate) {
			cell.setCellValue((LocalDate) obj);
			setDateFormat(workbook, cell);
		} else if (obj instanceof LocalDateTime) {
			setDateFormat(workbook, cell);
			cell.setCellValue((LocalDateTime) obj);
		} else if (obj instanceof Calendar) {
			setDateFormat(workbook, cell);
			cell.setCellValue((Calendar) obj);
		} else if (obj instanceof Date) {
			setDateFormat(workbook, cell);
			cell.setCellValue((Date) obj);
		} else if (obj instanceof Date) {
			cell.setCellValue((Date) obj);
		} else if (obj instanceof Number) {
			cell.setCellValue(converters.convertObject(obj, Double.class).doubleValue());
		} else if (obj == null) {
			cell.setBlank();
		} else {
			if (converters.isConvertable(obj.getClass())) {
				cell.setCellValue(converters.convertString(obj));
			} else {
				cell.setCellValue(obj.toString());
			}
		}
	}

	/**
	 * セルに値を設定します
	 * 
	 * @param converters コンバーター
	 * @param cell       セル
	 * @param obj        設定する値
	 */
	public static void setCell(final Converters converters, final Cell cell, final Object obj) {
		setCell(converters, cell.getSheet().getWorkbook(), cell, obj);
	}

	private static void setDateFormat(final Workbook workbook, final Cell cell) {
		if (0 == cell.getCellStyle().getDataFormat()) {
			// データフォーマットが標準の場合、年月日書式に変更
			DataFormat xssformat = workbook.createDataFormat();
			CellStyle style = workbook.createCellStyle();
			style.cloneStyleFrom(cell.getCellStyle());
			style.setDataFormat(xssformat.getFormat("yyyy/mm/dd"));
			cell.setCellStyle(style);
		}
	}

	/**
	 * セルにコメントを設定します
	 * 
	 * @param cell セル
	 * @param text コメント
	 */
	public static void setComment(final Cell cell, final String text) {
		setComment(cell.getSheet().getWorkbook().getCreationHelper(), cell, text);
	}

	/**
	 * セルにコメントを設定します
	 * 
	 * @param helper CreationHelper
	 * @param cell   セル
	 * @param text   コメント
	 */
	public static void setComment(final CreationHelper helper, final Cell cell, final String text) {
		final int dx1 = 200, dy1 = 100, dx2 = 200, dy2 = 100;
		final int col1 = cell.getColumnIndex() + 1;
		final int row1 = cell.getRowIndex();
		final int col2 = col1 + 3;
		final String[] args = text.split("\n");
		final int row2 = row1 + args.length + 1;
		final Drawing<?> drawing = cell.getSheet().createDrawingPatriarch();
		final ClientAnchor anchor = drawing.createAnchor(dx1, dy1, dx2, dy2, col1, row1, col2, row2);
		final Comment comment = drawing.createCellComment(anchor);
		// comment.setAuthor(author);
		comment.setString(helper.createRichTextString(text));
		cell.setCellComment(comment);
	}

	/**
	 * セルに値を設定します
	 * 
	 * @param workbook Workbook
	 * @param cell     Cell
	 * @param obj      value
	 */
	public static void setCell(final Workbook workbook, final Cell cell, final Object obj) {
		setCell(Converters.getDefault(), workbook, cell, obj);
	}

	/**
	 * セルに値を設定します
	 * 
	 * @param cell Cell
	 * @param obj  value
	 */
	public static void setCell(final Cell cell, final Object obj) {
		setCell(Converters.getDefault(), cell.getSheet().getWorkbook(), cell, obj);
	}

	/**
	 * セルスタイルを生成します
	 * 
	 * @param workbook    Workbook
	 * @param borderStyle BorderStyle
	 * @param color       color
	 * @return CellStyle
	 */
	public static CellStyle createCellStyle(final Workbook workbook, final BorderStyle borderStyle, short color) {
		CellStyle cellStyle = workbook.createCellStyle();
		setCellStyle(cellStyle, borderStyle, color);
		return cellStyle;
	}

	/**
	 * セルスタイルを生成します
	 * 
	 * @param workbook    Workbook
	 * @param borderStyle BorderStyle
	 * @param color       color
	 * @return CellStyle
	 */
	public static CellStyle createCellStyle(final Workbook workbook, final BorderStyle borderStyle,
			IndexedColors color) {
		return createCellStyle(workbook, borderStyle, color.getIndex());
	}

	/**
	 * セルスタイルを設定します
	 * 
	 * @param cellStyle   CellStyle
	 * @param borderStyle BorderStyle
	 * @param color       color
	 */
	public static void setCellStyle(CellStyle cellStyle, final BorderStyle borderStyle, short color) {
		if (borderStyle != null) {
			cellStyle.setBorderBottom(borderStyle);
			cellStyle.setBorderLeft(borderStyle);
			cellStyle.setBorderRight(borderStyle);
			cellStyle.setBorderTop(borderStyle);
		}
		cellStyle.setFillBackgroundColor(color);
	}

	/**
	 * セルコメントを設定します
	 * 
	 * @param cell        Cell
	 * @param font        Font
	 * @param cellComment cellComment
	 */
	public static void setCellComment(Cell cell, Font font, String cellComment) {
		Comment comment = cell.getCellComment();
		if (comment == null) {
			cell.removeCellComment();
		}
		CreationHelper creationHelper = cell.getSheet().getWorkbook().getCreationHelper();
		RichTextString richTextString = creationHelper.createRichTextString(cellComment);
		if (font == null) {
			font = cell.getSheet().getWorkbook().createFont();
			font.setColor(Font.COLOR_RED);
			font.setFontHeightInPoints((short) 14);
		}
		richTextString.applyFont(0, cellComment.length(), font);
		ClientAnchor anchor = creationHelper.createClientAnchor();
		anchor.setCol1(cell.getColumnIndex());
		anchor.setCol2(cell.getColumnIndex() + 10);
		anchor.setRow1(cell.getRow().getRowNum());
		anchor.setRow2(cell.getRow().getRowNum() + 10);
		Drawing<?> drawing = cell.getSheet().createDrawingPatriarch();
		comment = drawing.createCellComment(anchor);
		cell.setCellComment(comment);
	}

	/**
	 * セルスタイルを設定します
	 * 
	 * @param cellStyle   CellStyle
	 * @param borderStyle BorderStyle
	 * @param color       color
	 */
	public static void setCellStyle(final BorderStyle borderStyle, IndexedColors color, CellStyle cellStyle) {
		setCellStyle(cellStyle, borderStyle, color.getIndex());
	}

	/**
	 * シート内のセルの値をクリアします
	 * 
	 * @param sheet シート
	 */
	public static void clearCellValues(final Sheet sheet) {
		final Iterator<Row> itr = sheet.rowIterator();
		while (itr.hasNext()) {
			final Row row = itr.next();
			if (row.getRowNum() == 0) {
				continue;
			}
			final Iterator<Cell> citr = row.cellIterator();
			while (citr.hasNext()) {
				final Cell cell = citr.next();
				if (cell == null) {
					continue;
				}
				if (CellType.FORMULA == cell.getCellType()) {
					continue;
				}
				cell.setCellValue((String) null);
			}
		}
	}

}
