/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import java.util.regex.Matcher;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher.MatcherColumn;
import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.util.CommonUtils;

public class MySqlNumberMatcherColumn implements MatcherColumn {

	@Override
	public void apply(Matcher m, TypeInformation c) {
		String value = m.group("dataTypeName");
		if (value != null) {
			// c.setDataTypeName(dataTypeName[0]);
		}
		// String length = m.group("length");
		if (!CommonUtils.isEmpty(m.group("zerofill"))) {
			c.setSpecifics("zerofill", "true");
		}
		String width = m.group("width");
		if (!CommonUtils.isEmpty(width)) {
			c.setSpecifics("width", width);
		}
	}

}
