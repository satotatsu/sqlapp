/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.db.datatype.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcherWrapper;
import com.sqlapp.data.db.datatype.util.TypeInformation;

public class SpannerArrayColumnTypeMatcher implements ColumnTypeMatcher, ColumnTypeMatcherWrapper {

	private static final Pattern ARRAY_PATTERN = Pattern.compile("ARRAY<(?<dataTypeName>[^>]+)>",
			Pattern.CASE_INSENSITIVE);

	private ColumnTypeMatcher internalMatcher;

	public SpannerArrayColumnTypeMatcher(ColumnTypeMatcher internalMatcher) {
		this.internalMatcher = internalMatcher;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		Matcher matcher = ARRAY_PATTERN.matcher(productDataType);
		String typeName = productDataType;
		if (matcher.matches()) {
			typeName = matcher.group("dataTypeName").trim();
			Optional<TypeInformation> op = internalMatcher.match(typeName);
			if (op.isPresent()) {
				op.get().setArrayDimension(1);
				return op;
			} else {
				return Optional.empty();
			}
		}
		Optional<TypeInformation> op = internalMatcher.match(typeName);
		return op;
	}

	@Override
	public ColumnTypeMatcher getInternal() {
		return internalMatcher;
	}

}
