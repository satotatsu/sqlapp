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
