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
 * 正規表現でLengthを持つカラムの型を処理します
 */
public class LengthColumnTypeMatcher implements ColumnTypeMatcher, DefaultLength {

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public LengthColumnTypeMatcher(String dataTypeName) {
		this.internalColumnTypeMatcher = createColumnTypeMatcher(dataTypeName);
	}

	public LengthColumnTypeMatcher(String prefix, String suffix) {
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				prefix + "\\s*(\\(((?<length>\\s*[0-9]+)\\s*(?<multi>K|M|G)?(?<byte>CHAR|BYTE|C|B)?)\\))?\\s*" + suffix,
				(m, c) -> {
					String bt = m.group("byte");
					if (bt != null) {
						c.setCharacterSemantics(bt);
					}
					String length = m.group("length");
					String multi = m.group("multi");
					if (length != null) {
						// if ("BYTE".equalsIgnoreCase(dataTypeName))
						c.setLength(length, multi);
					} else {
						setTypeInformationLength(c);
					}
				});
	}

	private RegexColumnTypeMatcher createColumnTypeMatcher(String dataTypeName) {
		if (dataTypeName.contains("(") && dataTypeName.contains(")")) {
			return new RegexColumnTypeMatcher(
					"(?<dataTypeName>" + dataTypeName.replace("(", "\\(").replace(")", "\\)") + ")\\s*", (m, c) -> {
						String value = m.group("dataTypeName");
						if (value != null) {
							// c.setDataTypeName(value.toUpperCase());
						}
						setTypeInformationLength(c);
					});
		} else {
			return new RegexColumnTypeMatcher(
					"(?<dataTypeName>" + dataTypeName
							+ ")\\s*(\\(((?<length>\\s*[0-9]+)\\s*(?<multi>K|M|G)?(?<byte>CHAR|BYTE|C|B)?)\\))?",
					(m, c) -> {
						String value = m.group("dataTypeName");
						if (value != null) {
							// c.setDataTypeName(value.toUpperCase());
						}
						String bt = m.group("byte");
						if (bt != null) {
							c.setCharacterSemantics(bt);
						}
						String length = m.group("length");
						String multi = m.group("multi");
						if (length != null) {
							// if ("BYTE".equalsIgnoreCase(dataTypeName))
							c.setLength(length, multi);
						} else {
							setTypeInformationLength(c);
						}
					});
		}
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

	@Override
	public void setDefaultLength(Supplier<Long> supplier) {
		this.defaultLengthSupplier = supplier;
	}

	private Supplier<Long> defaultLengthSupplier;

	@Override
	public Supplier<Long> getDefaultLength() {
		return defaultLengthSupplier;
	}

}
