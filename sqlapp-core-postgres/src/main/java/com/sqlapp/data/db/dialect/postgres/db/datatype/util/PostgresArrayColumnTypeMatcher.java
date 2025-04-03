/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.db.datatype.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcherWrapper;
import com.sqlapp.data.db.datatype.util.TypeInformation;

public class PostgresArrayColumnTypeMatcher implements ColumnTypeMatcher, ColumnTypeMatcherWrapper {

	private static final Pattern ARRAY_PATTERN = Pattern.compile("(?<array>(\\[\\s*([0-9])*\\s*\\]))");

	private ColumnTypeMatcher internalMatcher;

	public PostgresArrayColumnTypeMatcher(ColumnTypeMatcher internalMatcher) {
		this.internalMatcher = internalMatcher;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		Matcher matcher = ARRAY_PATTERN.matcher(productDataType);
		String typeName = productDataType;
		int i = 0;
		while (matcher.find()) {
			if (i == 0) {
				typeName = typeName.substring(0, matcher.start()).trim();
			}
			i++;
		}
		Optional<TypeInformation> op = internalMatcher.match(typeName);
		if (op.isPresent()) {
			if (i > 0) {
				op.get().setArrayDimension(i);
			}
		}
		return op;
	}

	@Override
	public ColumnTypeMatcher getInternal() {
		return internalMatcher;
	}

}
