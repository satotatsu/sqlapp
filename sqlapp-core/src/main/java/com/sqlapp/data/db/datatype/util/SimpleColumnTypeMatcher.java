/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.datatype.util;

import java.util.Optional;

import com.sqlapp.util.CommonUtils;

/**
 * 名前でカラムの型を処理します
 */
public class SimpleColumnTypeMatcher implements ColumnTypeMatcher {

	private String[] patterns;

	public SimpleColumnTypeMatcher(String... patterns) {
		this.patterns = new String[patterns.length];
		for (int i = 0; i < patterns.length; i++) {
			this.patterns[i] = patterns[i].trim();
		}
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		for (int i = 0; i < patterns.length; i++) {
			if (CommonUtils.eqIgnoreCase(this.patterns[i], productDataType)) {
				final TypeInformation column = new TypeInformation();
				// column.setDataTypeName(this.patterns[0].toUpperCase());
				return Optional.of(column);
			}
		}
		return Optional.empty();
	}
}
