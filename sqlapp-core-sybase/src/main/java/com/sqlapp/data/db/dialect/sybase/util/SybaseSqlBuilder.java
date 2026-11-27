/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.NamedArgumentCollection;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class SybaseSqlBuilder extends AbstractSqlBuilder<SybaseSqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3976029895381266407L;

	public SybaseSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	public SybaseSqlBuilder identity() {
		appendElement("IDENTITY");
		return instance();
	}

	@Override
	protected SybaseSqlBuilder typeDefinition(Column column) {
		if (column.getDomain() != null)
			return super.typeDefinition(column);
		DataType type = column.getDataType();
		Long precision = column.getLength();
		Integer scale = column.getScale();
		if (type == DataType.NUMERIC || type == DataType.DECIMAL) {
			long p = precision == null ? 18 : precision;
			int s = scale == null ? 0 : scale;
			if (p < 1 || p > 38 || s < 0 || s > p)
				throw new IllegalArgumentException("ASE numeric precision must be 1..38 and scale 0..precision");
		}
		if (type == DataType.FLOAT && precision != null && (precision < 1 || precision > 48))
			throw new IllegalArgumentException("ASE FLOAT precision must be 1..48");
		if (type == DataType.ANY_DATA)
			throw new IllegalArgumentException(
					"ASE has no generic VARIANT type; select a concrete Schema data type or domain");
		String nativeName = column.getDataTypeName();
		if (type == DataType.NCHAR && "UNICHAR".equalsIgnoreCase(nativeName)
				|| type == DataType.NVARCHAR && "UNIVARCHAR".equalsIgnoreCase(nativeName)) {
			long length = precision == null ? 1 : precision;
			if (length < 1 || length > 4000)
				throw new IllegalArgumentException("ASE Unicode character length must be 1..4000");
			return _add(nativeName.toUpperCase(java.util.Locale.ROOT))._add("(" + length + ")");
		}
		return super.typeDefinition(column);
	}

	@Override
	protected SybaseSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		if ((column.getIdentityStep() != null && column.getIdentityStep() != 1L)
				|| (column.getIdentityStartValue() != null && column.getIdentityStartValue() != 1L)) {
			throw new IllegalArgumentException("ASE IDENTITY column " + column.getName()
					+ " cannot declare a custom start or increment in column DDL");
		}
		return identity();
	}

	@Override
	public SybaseSqlBuilder definition(final Column column, final boolean withRemarks) {
		if (column.getFormula() == null) {
			return super.definition(column, withRemarks);
		}
		if (column.getFormula().isBlank() || column.isIdentity() || column.getDefaultValue() != null
				|| column.getCheck() != null || column.getOnUpdate() != null) {
			throw new IllegalArgumentException("ASE computed column " + column.getName()
					+ " requires a nonempty formula without identity, default, check or on-update clauses");
		}
		as().space()._add(column.getFormula());
		space()._add(column.isFormulaPersisted() ? "MATERIALIZED" : "NOT MATERIALIZED");
		return instance();
	}

	public SybaseSqlBuilder identityInsert() {
		appendElement("IDENTITY_INSERT");
		return instance();
	}

	public SybaseSqlBuilder permissionSet() {
		appendElement("PERMISSION_SET");
		return instance();
	}

	public SybaseSqlBuilder aggregate() {
		appendElement("AGGREGATE");
		return instance();
	}

	public SybaseSqlBuilder readonly() {
		appendElement("READONLY");
		return instance();
	}

	public SybaseSqlBuilder scheme() {
		appendElement("SCHEME");
		return instance();
	}

	public SybaseSqlBuilder range() {
		appendElement("RANGE");
		return instance();
	}

	public SybaseSqlBuilder enable() {
		appendElement("ENABLE");
		return instance();
	}

	public SybaseSqlBuilder disable() {
		appendElement("DISABLE");
		return instance();
	}

	public SybaseSqlBuilder changeTracking() {
		appendElement("CHANGE_TRACKING");
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractSqlBuilder#appendArgumentBefore(com.sqlapp.data
	 * .schemas.NamedArgument)
	 */
	@Override
	protected void argumentBefore(NamedArgument obj) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractSqlBuilder#appendArgumentAfter(com.sqlapp.data
	 * .schemas.NamedArgument)
	 */
	@Override
	protected void argumentAfter(NamedArgument obj) {
		argumentDirection(obj);
		if (!CommonUtils.isEmpty(obj.getDefaultValue())) {
			this.space().eq().space()._add(obj.getDefaultValue());
		}
		if (obj.getReadonly() != null && obj.getReadonly().booleanValue()) {
			this.space().readonly();
		}
	}

	@Override
	public SybaseSqlBuilder arguments(NamedArgumentCollection<?> arguments) {
		if (arguments.getParent() instanceof Procedure) {
			return arguments("\n\t", arguments, "", "\n\t, ");
		} else {
			return arguments("(", arguments, ")", ", ");
		}
	}

	@Override
	public SybaseSqlBuilder clone() {
		return (SybaseSqlBuilder) super.clone();
	}
}
