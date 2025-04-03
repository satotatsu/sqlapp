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
				prefix + "\\s*(\\(((?<length>\\s*[0-9]+)\\s*)\\))?\\s*" + suffix, (m, c) -> {
					String length = m.group("length");
					if (length != null) {
						c.setLength(length);
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
