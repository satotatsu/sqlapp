/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.db.datatype.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcherWrapper;
import com.sqlapp.data.db.datatype.util.TypeInformation;

public class SqlServerNumberColumnTypeMatcher implements ColumnTypeMatcher, ColumnTypeMatcherWrapper {

	private static final Pattern PATTERN = Pattern.compile("(?<dataTypeName>.*?)\\S+IDENTITY");

	private ColumnTypeMatcher internalMatcher;

	public SqlServerNumberColumnTypeMatcher(ColumnTypeMatcher internalMatcher) {
		this.internalMatcher = internalMatcher;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		Matcher matcher = PATTERN.matcher(productDataType);
		String typeName = productDataType;
		if (matcher.matches()) {
			typeName = matcher.group("dataTypeName");
			Optional<TypeInformation> op = internalMatcher.match(typeName);
			if (op.isPresent()) {
				op.get().setIdentity(true);
			}
			return op;
		}
		Optional<TypeInformation> op = internalMatcher.match(typeName);
		return op;
	}

	@Override
	public ColumnTypeMatcher getInternal() {
		return internalMatcher;
	}

}
