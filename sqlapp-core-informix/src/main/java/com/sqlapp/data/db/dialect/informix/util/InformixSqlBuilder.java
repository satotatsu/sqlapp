/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import java.math.BigInteger;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/** Informix uses SERIAL types instead of an identity column clause. */
public class InformixSqlBuilder extends AbstractSqlBuilder<InformixSqlBuilder> {

	public InformixSqlBuilder(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected InformixSqlBuilder typeDefinition(final Column column) {
		if (column.getDomain() != null)
			return super.typeDefinition(column);
		final String name = column.getDataTypeName() == null ? ""
				: column.getDataTypeName().toUpperCase(java.util.Locale.ROOT).strip();
		if (!column.isIdentity()) {
			if (name.startsWith("DATETIME ")) {
				if (column.getDataType() != com.sqlapp.data.db.datatype.DataType.TIMESTAMP
						&& column.getDataType() != com.sqlapp.data.db.datatype.DataType.DATETIME
						&& column.getDataType() != com.sqlapp.data.db.datatype.DataType.TIME)
					throw new IllegalArgumentException(
							"Informix DATETIME declaration requires a temporal column: " + column.getName());
				return _add(InformixTemporalTypes.normalize(name));
			}
			if (name.startsWith("INTERVAL ")) {
				if (column.getDataType() == null || !column.getDataType().name().startsWith("INTERVAL"))
					throw new IllegalArgumentException(
							"Informix INTERVAL declaration requires an interval column: " + column.getName());
				return _add(InformixTemporalTypes.normalize(name));
			}
			if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.BIGINT
					&& ("BIGINT".equals(name) || "INT8".equals(name)))
				return _add(name);
			if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.DECIMALFLOAT
					|| name.matches("DECIMAL\\([0-9]+\\)")) {
				Long precision = column.getLength();
				if (precision == null && name.matches("DECIMAL\\([0-9]+\\)"))
					precision = Long.valueOf(name.substring(8, name.length() - 1));
				if ((column.getDataType() != com.sqlapp.data.db.datatype.DataType.DECIMAL
						&& column.getDataType() != com.sqlapp.data.db.datatype.DataType.DECIMALFLOAT)
						|| column.getScale() != null || precision == null || precision < 1 || precision > 32)
					throw new IllegalArgumentException("Invalid Informix floating DECIMAL column: " + column.getName());
				return _add("DECIMAL(")._add(precision)._add(')');
			}
			return super.typeDefinition(column);
		}
		if (column.getIdentityStep() != null && !BigInteger.ONE.equals(column.getIdentityStep())) {
			throw new IllegalArgumentException("Informix SERIAL increment must be 1: " + column.getName());
		}
		switch (column.getDataType()) {
		case INT, SERIAL -> _add("SERIAL");
		case BIGINT, BIGSERIAL -> _add("BIGSERIAL".equals(name) || "BIGINT".equals(name) ? "BIGSERIAL" : "SERIAL8");
		default -> throw new IllegalArgumentException("Unsupported Informix SERIAL type: " + column.getDataType());
		}
		if (column.getIdentityStartValue() != null) {
			_add('(')._add(column.getIdentityStartValue())._add(')');
		}
		return this;
	}

	@Override
	protected InformixSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		return this;
	}

	@Override
	protected InformixSqlBuilder notNullDefinition(final Column column) {
		String name = column.getSpecifics().get(InformixConstraintModes.NOT_NULL_NAME);
		String owner = column.getSpecifics().get(InformixConstraintModes.NOT_NULL_OWNER);
		String setting = column.getSpecifics().get(InformixConstraintModes.NOT_NULL_MODE);
		if (column.getTable() != null) {
			final var constraints = column.getTable().getConstraints().getNotNullConstraints().stream()
					.filter(c -> java.util.Objects.equals(column.getName(), c.getColumnName())).toList();
			if (constraints.size() > 1)
				throw new IllegalArgumentException(
						"Multiple Informix NOT NULL constraints for column: " + column.getName());
			if (!constraints.isEmpty()) {
				final var constraint = constraints.get(0);
				if (constraint.isNoInherit() || !constraint.isValidated())
					throw new IllegalArgumentException("Unsupported Informix NOT NULL options: " + column.getName());
				name = constraint.getName();
				owner = constraint.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER);
				setting = InformixConstraintModes.sql(constraint);
				if (name == null && setting == null)
					return space().notNull();
			}
		}
		if (name == null && setting == null && owner == null)
			return super.notNullDefinition(column);
		String mode = InformixConstraintModes.notNullMode(setting);
		if (!column.isNotNull() && !"DISABLED".equals(mode))
			throw new IllegalArgumentException(
					"Enabled Informix NOT NULL constraint requires a non-nullable column: " + column.getName());
		space().notNull();
		if (name != null) {
			if (name.isBlank() || owner != null && owner.isBlank())
				throw new IllegalArgumentException(
						"Blank Informix NOT NULL constraint identifier: " + column.getName());
			space().constraint().space();
			if (owner != null)
				name(owner.replace("\"", "\"\""))._add('.');
			name(name.replace("\"", "\"\""));
		} else if (owner != null)
			throw new IllegalArgumentException(
					"Informix NOT NULL owner requires a constraint name: " + column.getName());
		if (!"ENABLED".equals(mode))
			space()._add(mode);
		return this;
	}
}
