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
