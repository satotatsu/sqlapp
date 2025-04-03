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
