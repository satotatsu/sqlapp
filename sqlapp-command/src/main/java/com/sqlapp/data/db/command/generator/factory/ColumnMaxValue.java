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

import com.sqlapp.data.db.command.generator.setting.TableDataGeneratorSetting;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public class ColumnMaxValue implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	@Override
	public String apply(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return calculateBooleanMaxValue(column);
		}
		if (column.getDataType() == DataType.NUMERIC || column.getDataType() == DataType.DECIMAL) {
			return "" + calculateDecimalMaxValue(column);
		}
		if (column.getDataType().isNumeric()) {
			return calculateNumericMaxValue(column);
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			return calculateDateTimeMaxValue(column);
		}
		if (column.getDataType() == DataType.TIME) {
			return calculateTimeMaxValue(column);
		}
		if (column.getDataType() == DataType.DATE) {
			return calculateDateMaxValue(column);
		}
		return calculateOtherMaxValue(column);
	}

	protected String calculateOtherMaxValue(Column column) {
		return null;
	}

	protected String calculateBooleanMaxValue(Column column) {
		return null;
	}

	protected String calculateTimeMaxValue(Column column) {
		return null;
	}

	protected String calculateDateTimeMaxValue(Column column) {
		return "addMonths(" + TableDataGeneratorSetting.START_KEY + "." + column.getName() + ",1)";
	}

	protected String calculateDateMaxValue(Column column) {
		return "addMonths(" + TableDataGeneratorSetting.START_KEY + "." + column.getName() + ",1)";
	}

	protected long calculateDecimalMaxValue(Column column) {
		Long length = column.getLength();
		Integer scale = column.getScale();
		long len;
		if (length != null) {
			if (scale == null) {
				len = length.longValue();
			} else {
				len = length.longValue() - scale.intValue();
			}
		} else {
			return (long) Math.pow(10, 8);
		}
		return (long) Math.pow(10, len);
	}

	protected String calculateNumericMaxValue(Column column) {
		if (column.getDataType().getMaxValue() != null) {
			return "" + column.getDataType().getMaxValue();
		}
		return null;
	}

}
