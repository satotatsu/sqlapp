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

import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public class ColumnNextValue implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	private ColumnDefaultCharacterExpression charExpression = new ColumnDefaultCharacterExpression();
	private ColumnUUIDExpression uuidExpression = new ColumnUUIDExpression();

	@Override
	public String apply(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return "!" + TableGeneratorSetting.PREVIOUS_KEY + "." + column.getName();
		}
		if (column.getDataType() == DataType.DOUBLE) {
			return "nextDouble(0.0d, 1000.0d)";
		}
		if (column.getDataType() == DataType.FLOAT) {
			return "nextDouble(0.0f, 1000.0f)";
		}
		if (column.getDataType().isNumeric()) {
			return "" + TableGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + " + 1";
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			return "addMilliSeconds(" + TableGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.TIME) {
			return "addSeconds(" + TableGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.DATE) {
			return "addDays(" + TableGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
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
