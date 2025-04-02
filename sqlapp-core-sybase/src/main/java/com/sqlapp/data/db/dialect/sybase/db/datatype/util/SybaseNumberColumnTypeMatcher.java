package com.sqlapp.data.db.dialect.sybase.db.datatype.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcherWrapper;
import com.sqlapp.data.db.datatype.util.TypeInformation;

public class SybaseNumberColumnTypeMatcher implements ColumnTypeMatcher, ColumnTypeMatcherWrapper {

	private static final Pattern PATTERN = Pattern.compile("(?<dataTypeName>.*?)\\S+IDENTITY");

	private ColumnTypeMatcher internalMatcher;

	public SybaseNumberColumnTypeMatcher(ColumnTypeMatcher internalMatcher) {
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
