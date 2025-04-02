package com.sqlapp.data.db.datatype.util;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 正規表現でPrecision,Scaleを持つカラムの型を処理します
 */
public class PrecisionScaleColumnTypeMatcher implements ColumnTypeMatcher, DefaultPrecision, DefaultScale {

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public PrecisionScaleColumnTypeMatcher(String dataTypeName) {
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher("(?<dataTypeName>" + dataTypeName
				+ ")\\s*(\\((?<length>\\s*[0-9]+\\s*)(\\s*,\\s*(?<scale>\\s*[0-9]+\\s*))?\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(value);
					} else {
						setTypeInformationPrecision(c);
					}
					value = m.group("scale");
					if (value != null) {
						c.setScale(value);
					} else {
						setTypeInformationScale(c);
					}
				});
	}

	public PrecisionScaleColumnTypeMatcher(String prefix, String middle, String suffix) {
		// "INTERVAL DAY(", ") TO MINUTE(", ")"
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(prefix + "\\s*(\\((?<length>\\s*[0-9]+\\s*)\\s*"
				+ middle + "\\s*(\\s*,\\s*(?<scale>\\s*[0-9]+\\s*))?\\))?\\s*" + suffix, (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(value);
					} else {
						setTypeInformationPrecision(c);
					}
					value = m.group("scale");
					if (value != null) {
						c.setScale(value);
					} else {
						setTypeInformationScale(c);
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

	private Supplier<Integer> defaultScaleSupplier;

	@Override
	public void setDefaultScale(Supplier<Integer> supplier) {
		this.defaultScaleSupplier = supplier;
	}

	@Override
	public Supplier<Integer> getDefaultScale() {
		return defaultScaleSupplier;
	}

	@Override
	public Supplier<Integer> getDefaultPrecision() {
		return defaultPrecisionSupplier;
	}

}
