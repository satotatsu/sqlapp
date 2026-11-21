/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.schemas;

/**
 * スキーマ比較用のデフォルトのハンドラー
 * 
 * @author 竜夫
 *
 */
public class DefaultSchemaEqualsHandler extends ExcludeFilterEqualsHandler {

	public static final DefaultSchemaEqualsHandler DEFAULT_INSTANCE = new DefaultSchemaEqualsHandler();

	/**
	 * Next sequence/identity values. They advance with every insert, so comparing
	 * them by default would report a difference between any two live databases.
	 */
	private static final java.util.Set<String> SEQUENCE_VALUE_PROPERTIES = java.util.Set.of(
			SchemaProperties.LAST_VALUE.getLabel(), SchemaProperties.IDENTITY_LAST_VALUE.getLabel());

	/** Whether next sequence/identity values are compared. */
	private boolean compareSequenceValues = false;

	public DefaultSchemaEqualsHandler() {
		super(SchemaProperties.CREATED_AT.getLabel(), SchemaProperties.LAST_ALTERED_AT.getLabel(),
				SchemaProperties.STATISTICS.getLabel(), SchemaProperties.DISPLAY_NAME.getLabel(),
				SchemaProperties.DISPLAY_REMARKS.getLabel(), SchemaProperties.VIRTUAL.getLabel(),
				SchemaProperties.OCTET_LENGTH.getLabel(), SchemaObjectProperties.ROWS.getLabel());
	}

	/**
	 * @return true when {@code lastValue} and {@code identityLastValue} take part in
	 *         the comparison. Defaults to false.
	 */
	public boolean isCompareSequenceValues() {
		return compareSequenceValues;
	}

	/**
	 * Includes the next sequence/identity values in the comparison, for example to
	 * verify a migration or to generate RESTART statements. Do not change the
	 * shared {@link #DEFAULT_INSTANCE}; use a new instance instead.
	 *
	 * @param compareSequenceValues true to compare next values
	 */
	public DefaultSchemaEqualsHandler setCompareSequenceValues(final boolean compareSequenceValues) {
		this.compareSequenceValues = compareSequenceValues;
		return this;
	}

	@Override
	public boolean valueEquals(final String propertyName, final Object object1, final Object object2,
			final Object value1, final Object value2, final java.util.function.BooleanSupplier p) {
		if (!compareSequenceValues && SEQUENCE_VALUE_PROPERTIES.contains(propertyName)) {
			return true;
		}
		return super.valueEquals(propertyName, object1, object2, value1, value2, p);
	}

	@Override
	public DefaultSchemaEqualsHandler clone() {
		return (DefaultSchemaEqualsHandler) super.clone();
	}

}
