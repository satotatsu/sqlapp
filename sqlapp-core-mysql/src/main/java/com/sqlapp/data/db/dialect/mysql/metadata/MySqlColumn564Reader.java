/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

public class MySqlColumn564Reader extends MySqlColumnReader {

	protected MySqlColumn564Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Long getMaxLength(ExResultSet rs) throws SQLException{
		Long maxLength = getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numericPrecision = getLong(rs, "NUMERIC_PRECISION");
		Long datetimePrecision = getLong(rs, "DATETIME_PRECISION");
		return CommonUtils.coalesce(maxLength, numericPrecision, datetimePrecision);
	}

}
