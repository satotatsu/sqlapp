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

import java.time.LocalDate;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnMinValue implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	private ColumnFunction<String> charExpression = new ColumnDefaultCharacterExpression();
	private ColumnFunction<String> jsonExpression = new ColumnDefaultJsonExpression();
	private ColumnFunction<String> uuidExpression = new ColumnUUIDExpression();
	private Integer startYear;
	private Integer defaultStartYear = 2000;

	@Override
	public String apply(Column column) {
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
			return "" + Converters.getNewBooleanTrueInstance().convertObject(1, column.getDataType().getDefaultClass());
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			LocalDate dt = LocalDate.now();
			return "LocalDateTime.of(" + dt.getYear() + "," + dt.getMonthValue() + ",1,0,0,0)";
		}
		if (column.getDataType() == DataType.DATE) {
			LocalDate dt = LocalDate.now();
			return "LocalDate.of(" + dt.getYear() + "," + dt.getMonthValue() + ",1)";
		}
		if (column.getDataType() == DataType.TIME) {
			return "LocalTime.of(0,0,0)";
		}
		if (isYear(column)) {
			if (startYear != null) {
				return "" + startYear;
			}
			return "" + defaultStartYear;
		}
		if (isYearMonth(column)) {
			if (startYear != null) {
				return "YearMonth.of(" + startYear + ",1)";
			}
			return "YearMonth.of(" + defaultStartYear + ",1)";
		}
		if (column.getDataType().isJson()) {
			return getJsonExpression().apply(column);
		}
		if (column.getDataType().isJson()) {
			return getJsonExpression().apply(column);
		}
		if (column.getDataType().isCharacter()) {
			return getCharExpression().apply(column);
		}
		if (column.getDataType() == DataType.UUID) {
			return getUuidExpression().apply(column);
		}
		return null;
	}

	protected boolean isYear(Column column) {
		return isYearStatic(column);
	}

	protected boolean isYearMonth(Column column) {
		return isYearMonthStatic(column);
	}

	protected static boolean isYearStatic(Column column) {
		if (column.getDataType().isCharacter()) {
			if (column.getLength() == null) {
				return false;
			}
			if (column.getLength().longValue() != 4L) {
				return false;
			}
		}
		if ("YYYY".equalsIgnoreCase(column.getName())) {
			return true;
		}
		if ("YEAR".equalsIgnoreCase(column.getName())) {
			return true;
		}
		return false;
	}

	protected static boolean isYearMonthStatic(Column column) {
		if (!column.getDataType().isCharacter()) {
			return false;
		}
		if (column.getLength() == null) {
			return false;
		}
		if (column.getLength().longValue() != 6L) {
			return false;
		}
		if ("YM".equalsIgnoreCase(column.getName())) {
			return true;
		}
		if ("YEARMONTH".equalsIgnoreCase(column.getName())) {
			return true;
		}
		if ("YEAR_MONTH".equalsIgnoreCase(column.getName())) {
			return true;
		}
		return false;
	}
}
