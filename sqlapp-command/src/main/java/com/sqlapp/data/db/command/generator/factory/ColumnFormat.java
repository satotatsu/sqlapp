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

package com.sqlapp.data.db.command.generator.factory;

import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnFormat implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	@Override
	public String apply(Column column) {
		if (isYear(column)) {
			return null;
		}
		if (isYearMonth(column)) {
			return "formatDate( " + TableGeneratorConfig.VALUE + " , \"yyyyMM\" )";
		}
		return null;
	}

	protected boolean isYear(Column column) {
		return ColumnMinValue.isYearStatic(column);
	}

	protected boolean isYearMonth(Column column) {
		return ColumnMinValue.isYearMonthStatic(column);
	}
}
