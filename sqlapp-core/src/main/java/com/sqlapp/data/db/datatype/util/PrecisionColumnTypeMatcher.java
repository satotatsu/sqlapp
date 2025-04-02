package com.sqlapp.data.db.datatype.util;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 正規表現でPrecisionを持つカラムの型を処理します
 */
public class PrecisionColumnTypeMatcher implements ColumnTypeMatcher, DefaultPrecision {

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public PrecisionColumnTypeMatcher(String dataTypeName) {
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				"(?<dataTypeName>" + dataTypeName + ")\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						// c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(value);
					} else {
						setTypeInformationPrecision(c);
					}
				});
	}

	public PrecisionColumnTypeMatcher(String prefix, String suffix) {
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				prefix + "\\s*(\\(((?<length>\\s*[0-9]+)\\s*(?<multi>K|M|G)?)\\))?\\s*" + suffix, (m, c) -> {
					String length = m.group("length");
					String multi = m.group("multi");
					if (length != null) {
						// if ("BYTE".equalsIgnoreCase(dataTypeName))
						c.setLength(length, multi);
					} else {
						setTypeInformationPrecision(c);
					}
				});
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

	@Override
	public void setDefaultPrecision(Supplier<Integer> supplier) {
		this.defaultPrecisionSupplier = supplier;
	}

	private Supplier<Integer> defaultPrecisionSupplier;

	@Override
	public Supplier<Integer> getDefaultPrecision() {
		return defaultPrecisionSupplier;
	}

}
