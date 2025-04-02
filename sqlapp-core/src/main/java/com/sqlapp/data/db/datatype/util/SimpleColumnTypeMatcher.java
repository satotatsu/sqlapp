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
