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
