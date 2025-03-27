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

public class ColumnStartValue implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	private ColumnDefaultCharacterExpression charExpression = new ColumnDefaultCharacterExpression();
	private ColumnUUIDExpression uuidExpression = new ColumnUUIDExpression();

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
		if (column.getDataType() == DataType.TIME) {
			return "LocalTime.of(0,0,0)";
		}
		if (column.getDataType() == DataType.DATE) {
			LocalDate dt = LocalDate.now();
			return "LocalDate.of(" + dt.getYear() + "," + dt.getMonthValue() + ",1)";
		}
		if (column.getDataType().isCharacter()) {
			return charExpression.apply(column);
		}
		if (column.getDataType() == DataType.UUID) {
			return uuidExpression.apply(column);
		}
		return null;
	}

}
