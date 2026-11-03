/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;

/** Basic identity syntax shared by z/OS and IBM i. */
public class Db2HostSqlBuilder extends Db2SqlBuilder {

	private static final long serialVersionUID = 1L;

	public Db2HostSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Db2SqlBuilder typeDefinition(Column column) {
		var dialect = (AbstractDb2HostDialect) getDialect();
		var type = column.getDataType();
		if (type == null || getDialect().getDbDataTypes().getDbTypeStrict(type) == null)
			throw new IllegalArgumentException("Unsupported Db2 host column type: " + column.getName());
		long max = switch (type) {
		case CHAR, BINARY -> dialect.maxCharLength();
		case VARCHAR, VARBINARY -> dialect.maxVarcharLength();
		case NCHAR -> dialect.maxGraphicLength();
		case NVARCHAR -> dialect.maxVargraphicLength();
		default -> Long.MAX_VALUE;
		};
		if (column.getLength() != null && (column.getLength() < 1 || column.getLength() > max))
			throw new IllegalArgumentException("Invalid Db2 host column length: " + column.getName());
		if ((type == DataType.DECIMAL || type == DataType.NUMERIC) && column.getLength() != null) {
			if (column.getLength() > dialect.maxDecimalPrecision()
					|| (column.getScale() != null && (column.getScale() < 0 || column.getScale() > column.getLength())))
				throw new IllegalArgumentException("Invalid Db2 host decimal precision/scale: " + column.getName());
		}
		return super.typeDefinition(column);
	}

	@Override
	public Db2SqlBuilder cascadeRule(ForeignKeyConstraint constraint) {
		if (!constraint.isEnable())
			throw new IllegalArgumentException("Non-enforced Db2 host foreign keys are not supported");
		if (constraint.getDeleteRule() != null) {
			if (!getDialect().supportsRuleOnDelete(constraint.getDeleteRule()))
				throw new IllegalArgumentException("Unsupported Db2 host delete rule: " + constraint.getDeleteRule());
			space()._add("ON DELETE ")._add(action(constraint.getDeleteRule()));
		}
		if (constraint.getUpdateRule() != null) {
			if (!getDialect().supportsRuleOnUpdate(constraint.getUpdateRule()))
				throw new IllegalArgumentException("Unsupported Db2 host update rule: " + constraint.getUpdateRule());
			if (((AbstractDb2HostDialect) getDialect()).supportsExplicitUpdateRule())
				space()._add("ON UPDATE ")._add(action(constraint.getUpdateRule()));
		}
		return this;
	}

	private String action(CascadeRule rule) {
		return switch (rule) {
		case None -> "NO ACTION";
		case Cascade -> "CASCADE";
		case Restrict -> "RESTRICT";
		case SetNull -> "SET NULL";
		case SetDefault -> "SET DEFAULT";
		};
	}

	@Override
	protected Db2SqlBuilder autoIncrement(AbstractColumn<?> column) {
		if (column.getDefaultValue() != null)
			throw new IllegalArgumentException("Identity column cannot have DEFAULT: " + column.getName());
		generated();
		if (column.getIdentityGenerationType() == IdentityGenerationType.ByDefault)
			by().default_();
		else
			always();
		as().identity();
		var sequence = column.getSequence();
		if (sequence != null) {
			var options = new java.util.ArrayList<String>();
			if (sequence.getStartValue() != null)
				options.add("START WITH " + sequence.getStartValue());
			if (sequence.getIncrementBy() != null) {
				if (sequence.getIncrementBy().signum() == 0)
					throw new IllegalArgumentException("Identity increment cannot be zero");
				options.add("INCREMENT BY " + sequence.getIncrementBy());
			}
			if (sequence.getMinValue() != null)
				options.add("MINVALUE " + sequence.getMinValue());
			if (sequence.getMaxValue() != null)
				options.add("MAXVALUE " + sequence.getMaxValue());
			options.add(column.isIdentityCycle() ? "CYCLE" : "NO CYCLE");
			if (column.getIdentityCacheSize() != null)
				options.add(column.getIdentityCacheSize() == 0 ? "NO CACHE" : "CACHE " + column.getIdentityCacheSize());
			options.add(column.isIdentityOrder() ? "ORDER" : "NO ORDER");
			space()._add("(")._add(String.join(" ", options))._add(")");
		}
		return this;
	}
}
