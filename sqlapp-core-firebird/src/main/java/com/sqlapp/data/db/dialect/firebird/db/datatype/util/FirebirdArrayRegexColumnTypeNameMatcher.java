package com.sqlapp.data.db.dialect.firebird.db.datatype.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

public class FirebirdArrayRegexColumnTypeNameMatcher extends RegexColumnTypeMatcher {

	private static final Pattern ARRAY_PATTERN = Pattern
			.compile("(?<dataTypeName>.+)(?<array>(\\[\\s*[^\\]]+\\s*\\]))");

	public FirebirdArrayRegexColumnTypeNameMatcher(String pattern, MatcherColumn... matcherColumns) {
		super(pattern, matcherColumns);
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		Matcher matcher = ARRAY_PATTERN.matcher(productDataType);
		Optional<TypeInformation> op;
		if (matcher.matches()) {
			String typeName = matcher.group("dataTypeName");
			String arrayInfo = matcher.group("array");
			op = super.match(typeName);
			setArrayInfo(op.get(), arrayInfo.replaceAll("\\s+", ""));
		} else {
			op = super.match(productDataType);
		}
		return op;
	}

	private void setArrayInfo(TypeInformation column, String arrayInfo) {
		arrayInfo = arrayInfo.substring(0, arrayInfo.length() - 1);
		final String[] args = arrayInfo.split(",");
		column.setArrayDimension(args.length);
	}
}
