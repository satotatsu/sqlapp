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

package com.sqlapp.data.db.dialect;

import com.sqlapp.util.CommonUtils;

public class DialectUtils {
	private DialectUtils() {
	}

	/**
	 * デフォルトの文字列の最大桁数を返します。
	 * 
	 * @param value
	 */
	public static long getDefaultTypeLength(String value) {
		if (value == null) {
			return 255L;
		} else if (value.length() < 254) {
			return 254L;
		} else if (value.length() < 1023) {
			return 1023L;
		} else if (value.length() < 65535) {
			return 65535L;
		} else {
			return CommonUtils.LEN_1GB;
		}
	}
}
