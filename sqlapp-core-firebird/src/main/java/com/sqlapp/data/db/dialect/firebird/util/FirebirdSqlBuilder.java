/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Firebird用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class FirebirdSqlBuilder extends AbstractSqlBuilder<FirebirdSqlBuilder> {
	public static final String ARRAY_BOUNDS = "firebird.array.bounds";

	public FirebirdSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * GENERATOR句を追加します
	 * 
	 */
	public FirebirdSqlBuilder generator() {
		appendElement("GENERATOR");
		return instance();
	}

	/**
	 * RESTART句を追加します
	 * 
	 */
	public FirebirdSqlBuilder restart() {
		appendElement("RESTART");
		return instance();
	}

	/**
	 * ACTIVE句を追加します
	 * 
	 */
	public FirebirdSqlBuilder active() {
		appendElement("ACTIVE");
		return instance();
	}

	/**
	 * INACTIVE句を追加します
	 * 
	 */
	public FirebirdSqlBuilder inactive() {
		appendElement("INACTIVE");
		return instance();
	}

	/**
	 * POSITION句を追加します
	 * 
	 */
	public FirebirdSqlBuilder position() {
		appendElement("POSITION");
		return instance();
	}

	@Override
	protected FirebirdSqlBuilder typeDefinition(final Column column) {
		if (column.getDomain() != null) {
			if (column.getDomainName() == null || column.getDomainSchemaName() != null
					|| column.getDomainColumnName() != null || !column.getDomain().getColumns().isEmpty())
				throw new IllegalArgumentException("Firebird column domain requires an unqualified domain name");
			if (column.getCharacterSet() != null)
				throw new IllegalArgumentException(
						"Firebird domain column inherits CHARACTER SET; configure it on the domain, or override column COLLATE");
			if (column.isIdentity() || column.getFormula() != null)
				throw new IllegalArgumentException("Firebird domain column cannot also be identity or computed");
			return name(column.getDomainName());
		}
		if ("INT128".equalsIgnoreCase(column.getDataTypeName())) {
			if (getDialect().compareTo(DialectHolder.defaultDialect40) < 0) {
				throw new IllegalArgumentException("INT128 requires Firebird 4.0 or later");
			}
			_add("INT128");
		} else {
			super.typeDefinition(column);
		}
		if (column.getArrayDimension() > 0) {
			String bounds = column.getSpecifics().get(ARRAY_BOUNDS);
			if (bounds == null && column.getArrayDimension() == 1)
				bounds = column.getArrayDimensionLowerBound() + ":" + column.getArrayDimensionUpperBound();
			if (bounds == null || bounds.split(",", -1).length != column.getArrayDimension()
					|| column.getArrayDimension() > 16)
				throw new IllegalArgumentException(
						"Firebird array requires explicit bounds for each of up to 16 dimensions");
			for (String dimension : bounds.split(",")) {
				String[] range = dimension.split(":", -1);
				try {
					if (range.length != 2 || Integer.parseInt(range[0]) > Integer.parseInt(range[1]))
						throw new IllegalArgumentException("Invalid Firebird array bounds: " + bounds);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid Firebird array bounds: " + bounds, e);
				}
			}
			_add("[")._add(bounds)._add("]");
		}
		return instance();
	}

	public FirebirdSqlBuilder columnType(Column column) {
		return typeDefinition(column);
	}

	@Override
	protected FirebirdSqlBuilder characterSetDefinition(Column column) {
		if (column.getDomain() == null && column.getCharacterSet() != null)
			space()._add("CHARACTER SET ").name(column.getCharacterSet());
		return instance();
	}

	public FirebirdSqlBuilder typeDefinition(com.sqlapp.data.schemas.Domain domain) {
		Column type = new Column(domain.getName()).setDataType(domain.getDataType())
				.setDataTypeName(domain.getDataTypeName()).setLength(domain.getLength()).setScale(domain.getScale())
				.setArrayDimension(domain.getArrayDimension())
				.setArrayDimensionLowerBound(domain.getArrayDimensionLowerBound())
				.setArrayDimensionUpperBound(domain.getArrayDimensionUpperBound());
		type.getSpecifics().putAll(domain.getSpecifics());
		return typeDefinition(type);
	}

	@Override
	protected FirebirdSqlBuilder collateDefinition(Column column) {
		return instance();
	}

	private void appendCollation(Column column) {
		if (column.getCollation() != null)
			space()._add("COLLATE ").name(column.getCollation());
	}

	@Override
	public FirebirdSqlBuilder definition(Column column, boolean withRemarks) {
		if (column.getFormula() == null || column.getFormula().isBlank()) {
			super.definition(column, withRemarks);
			appendCollation(column);
			return instance();
		}
		if (column.isIdentity() || column.getArrayDimension() > 0 || column.getDefaultValue() != null)
			throw new IllegalArgumentException(
					"Firebird computed column cannot also specify identity, array or default");
		typeDefinition(column);
		characterSetDefinition(column);
		space()._add("COMPUTED BY (")._add(column.getFormula())._add(")");
		appendCollation(column);
		return instance();
	}

	@Override
	public FirebirdSqlBuilder argument(final NamedArgument argument) {
		name(argument).space();
		typeDefinition(argument);
		if (argument.getCharacterSet() != null)
			space()._add("CHARACTER SET ").name(argument.getCharacterSet());
		if (argument.isNotNull()) {
			space().not().null_();
		}
		if (argument.getCollation() != null)
			space()._add("COLLATE ").name(argument.getCollation());
		if (argument.getDirection() != ParameterDirection.Output && argument.getDefaultValue() != null) {
			space()._add("DEFAULT ")._add(argument.getDefaultValue());
		}
		return instance();
	}

	@Override
	protected FirebirdSqlBuilder autoIncrement(AbstractColumn<?> column) {
		var type = column.getDataType();
		boolean integral = type == com.sqlapp.data.db.datatype.DataType.SMALLINT
				|| type == com.sqlapp.data.db.datatype.DataType.INT
				|| type == com.sqlapp.data.db.datatype.DataType.BIGINT;
		boolean numeric = type == com.sqlapp.data.db.datatype.DataType.NUMERIC
				|| type == com.sqlapp.data.db.datatype.DataType.DECIMAL;
		if ((!integral && !numeric) || (column.getScale() != null && column.getScale() != 0)
				|| (numeric && column.getLength() != null && column.getLength() > 18) || column.getArrayDimension() > 0
				|| "INT128".equalsIgnoreCase(column.getDataTypeName()))
			throw new IllegalArgumentException(
					"Firebird identity requires a scalar integer or NUMERIC/DECIMAL with precision at most 18 and scale 0");
		if (column instanceof Column value && value.getDefaultValue() != null)
			throw new IllegalArgumentException("Firebird identity columns cannot have an explicit DEFAULT");
		if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
			throw new IllegalArgumentException("Identity columns require Firebird 3.0 or later");
		boolean always = column.getIdentityGenerationType() == com.sqlapp.data.schemas.IdentityGenerationType.Always;
		boolean modern = getDialect().compareTo(DialectHolder.defaultDialect40) >= 0;
		if (always && !modern)
			throw new IllegalArgumentException("GENERATED ALWAYS identity requires Firebird 4.0 or later");
		if (column.getIdentityStep() != null && column.getIdentityStep() != 1 && !modern)
			throw new IllegalArgumentException("Identity increments require Firebird 4.0 or later");
		if (column.getIdentityStep() != null && column.getIdentityStep() == 0)
			throw new IllegalArgumentException("Identity increment must not be zero");
		space().generated();
		if (always)
			always();
		else
			by().default_();
		as().identity();
		if (column.getIdentityStartValue() != null || column.getIdentityStep() != null && modern) {
			_add(" (");
			if (column.getIdentityStartValue() != null)
				_add("START WITH ")._add(column.getIdentityStartValue());
			if (column.getIdentityStep() != null && modern)
				space()._add("INCREMENT BY ")._add(column.getIdentityStep());
			_add(")");
		}
		return instance();
	}

	@Override
	public FirebirdSqlBuilder clone() {
		return (FirebirdSqlBuilder) super.clone();
	}
}
