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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正規表現でカラムの型を処理します
 */
public class RegexColumnTypeMatcher implements ColumnTypeMatcher {

	private final Pattern pattern;

	private final MatcherColumn[] matcherColumns;

	public RegexColumnTypeMatcher(String pattern, MatcherColumn... matcherColumns) {
		this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		this.matcherColumns = matcherColumns;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		final Matcher matcher = pattern.matcher(productDataType);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		final TypeInformation column = new TypeInformation();
		for (MatcherColumn matcherColumn : matcherColumns) {
			matcherColumn.apply(matcher, column);
		}
		return Optional.of(column);
	}

	@FunctionalInterface
	public static interface MatcherColumn {
		void apply(Matcher matcher, TypeInformation column);
	}

}
