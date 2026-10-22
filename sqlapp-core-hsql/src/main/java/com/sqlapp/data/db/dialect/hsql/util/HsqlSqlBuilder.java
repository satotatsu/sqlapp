/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.util;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.TableLockMode;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * HSQL用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class HsqlSqlBuilder extends AbstractSqlBuilder<HsqlSqlBuilder> {
	@Override
	public HsqlSqlBuilder argument(com.sqlapp.data.schemas.NamedArgument argument) {
		if (argument.getDirection() != null && argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
			_add(argument.getDirection()).space();
		if (argument.getName() != null) {
			if ("true".equals(argument.getSpecifics().get("hsql.argumentNameLiteral.v1")))
				name(argument.getName());
			else
				_add(argument.getName());
			space();
		}
		return typeDefinition(argument);
	}

	@Override
	public HsqlSqlBuilder cascadeRule(ForeignKeyConstraint constraint) {
		if (constraint.getDeleteRule() != null) {
			space().on().delete().space()._add(constraint.getDeleteRule() == CascadeRule.None ? "NO ACTION"
					: constraint.getDeleteRule().getSqlValue());
		}
		if (constraint.getUpdateRule() != null) {
			space().on().update().space()._add(constraint.getUpdateRule() == CascadeRule.None ? "NO ACTION"
					: constraint.getUpdateRule().getSqlValue());
		}
		return instance();
	}

	public HsqlSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * カラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	@Override
	protected HsqlSqlBuilder defaultDefinition(Column column) {
		if (column.isIdentity()) {
			Dialect dialect21 = DialectResolver.getInstance().getDialect("hsql", 2, 1);
			if (!CommonUtils.isEmpty(column.getSequenceName()) && this.getDialect().compareTo(dialect21) >= 0) {
				generated().by().space()._add("DEFAULT").as().sequence().space()._add(column.getSequenceName());
			} else {
				generated().by().space()._add("DEFAULT").as().identity();
			}
			// space().append("DEFAULT").as().identity();
			Long start = getAutoIncrementStart(column);
			if (start != null || column.getIdentityStep() != null) {
				space()._add("(").space();
			}
			if (start != null) {
				start().with().space()._add(start);
			}
			if (column.getIdentityStep() != null) {
				comma().incrementBy().space()._add(column.getIdentityStep());
			}
			if (start != null || column.getIdentityStep() != null) {
				space()._add(")");
			}
		} else {
			if (column.getDefaultValue() != null) {
				space()._add("DEFAULT").space()._add(column.getDefaultValue());
			}
		}
		return instance();
	}

	/**
	 * カラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	@Override
	protected HsqlSqlBuilder defaultDefinitionForAlter(Column column) {
		if (column.isIdentity()) {
			Dialect dialect21 = DialectResolver.getInstance().getDialect("hsql", 2, 1);
			if (!CommonUtils.isEmpty(column.getSequenceName()) && this.getDialect().compareTo(dialect21) >= 0) {
				generated().by().space()._add("DEFAULT").as().sequence().space()._add(column.getSequenceName());
			} else {
				generated().by().space()._add("DEFAULT").as().identity();
			}
			// space().append("DEFAULT").as().identity();
			Long start = getAutoIncrementStart(column);
			if (start != null || column.getIdentityStep() != null) {
				space()._add("(").space();
			}
			if (start != null) {
				start().with().space()._add(start);
			}
			if (column.getIdentityStep() != null) {
				comma().incrementBy().space()._add(column.getIdentityStep());
			}
			if (start != null || column.getIdentityStep() != null) {
				space()._add(")");
			}
		} else {
		}
		return instance();
	}

	/**
	 * カラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	public HsqlSqlBuilder appendAlterColumnDefaultDefinition(Column column) {
		if (column.isIdentity()) {
			Dialect dialect21 = DialectResolver.getInstance().getDialect("hsql", 2, 1);
			if (!CommonUtils.isEmpty(column.getSequenceName()) && this.getDialect().compareTo(dialect21) >= 0) {
				generated().by().space()._add("DEFAULT").as().sequence().space()._add(column.getSequenceName());
			} else {
				generated().by().space()._add("DEFAULT").as().identity();
			}
			// space().append("DEFAULT").as().identity();
			Long start = getAutoIncrementStart(column);
			if (start != null || column.getIdentityStep() != null) {
				space()._add("(").space();
			}
			if (start != null) {
				start().with().space()._add(start);
			}
			if (column.getIdentityStep() != null) {
				comma().incrementBy().space()._add(column.getIdentityStep());
			}
			if (start != null || column.getIdentityStep() != null) {
				space()._add(")");
			}
		} else {
			if (column.getDefaultValue() != null) {
				space().setDefault()._add(column.getDefaultValue());
			} else {
				space()._add("DROP DEFAULT");
			}
		}
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.AbstractSqlBuilder#appendColumnDefinition(com.sqlapp.data.
	 * schemas.Column)
	 */
	@Override
	public HsqlSqlBuilder definition(Column column) {
		return definition(column, true);
	}

	@Override
	public HsqlSqlBuilder definition(Column column, boolean withRemarks) {
		if (column.getDataType() == DataType.DOMAIN
				|| column.getDataType() == DataType.OTHER && !CommonUtils.isEmpty(column.getDataTypeName())) {
			this._add(column.getDataTypeName());
		} else {
			typeDefinition(column);
			characterSetDefinition(column);
			collateDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getFormula())) {
			space().generated().always().as().space()._add("(")._add(column.getFormula())._add(")");
		} else if (!CommonUtils.isEmpty(column.getDefaultValue()) && !column.isIdentity()) {
			defaultDefinition(column);
		}
		notNullDefinition(column);
		if (column.isIdentity()) {
			autoIncrement(column);
		}
		this.onUpdateDefinition(column);
		if (column.getCheck() != null) {
			checkConstraintDefinition(column);
		}
		if (withRemarks && !CommonUtils.isEmpty(column.getRemarks())) {
			comment(column);
		}
		return instance();
	}

	private Long getAutoIncrementStart(Column column) {
		if (column.getIdentityLastValue() != null) {
			return column.getIdentityLastValue();
		}
		if (column.getIdentityStartValue() != null) {
			return column.getIdentityStartValue();
		}
		return Long.valueOf(1);
	}

	@Override
	protected HsqlSqlBuilder notNullDefinitionForAlter(Column column) {
		return instance();
	}

	/**
	 * IDENTITY句を追加します
	 * 
	 */
	public HsqlSqlBuilder identity() {
		appendElement("IDENTITY");
		return instance();
	}

	/**
	 * INCREMENT BY句を追加します
	 * 
	 */
	public HsqlSqlBuilder incrementBy() {
		appendElement("INCREMENT BY");
		return instance();
	}

	public HsqlSqlBuilder lockMode(TableLockMode tableLockMode) {
		if (tableLockMode != null) {
			if (tableLockMode.isExclusive()) {
				appendElement("WRITE");
			} else {
				appendElement("READ");
			}
		}
		return instance();
	}

	@Override
	protected HsqlSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		if (column instanceof Column tableColumn && !CommonUtils.isEmpty(tableColumn.getSequenceName())) {
			if (column.getIdentityGenerationType() == IdentityGenerationType.Always
					|| getDialect().compareTo(DialectResolver.getInstance().getDialect("hsql", 2, 1)) < 0) {
				throw new IllegalArgumentException(
						"HSQLDB named sequence columns require 2.1+ and BY DEFAULT: " + column.getName());
			}
			generated().by().default_().as().sequence().space();
			var sequence = tableColumn.getSequence();
			if (sequence != null && sequence.getSchemaName() != null)
				name(sequence.getSchemaName())._add(".");
			name(tableColumn.getSequenceName());
			return instance();
		}
		generated();
		if (column.getIdentityGenerationType() == IdentityGenerationType.Always) {
			always();
		} else {
			by().default_();
		}
		as().identity();
		Long start = column.getIdentityLastValue() != null ? column.getIdentityLastValue()
				: column.getIdentityStartValue();
		if (start != null || column.getIdentityStep() != null || column.getIdentityMinValue() != null
				|| column.getIdentityMaxValue() != null || column.isIdentityCycle()) {
			if (start == null) {
				start = 1L;
			}
			final Long identityStart = start;
			this.space().brackets(() -> {
				start().with().space()._add(identityStart);
				if (column.getIdentityStep() != null) {
					comma().incrementBy().space()._add(column.getIdentityStep());
				}
				if (column.getIdentityMinValue() != null) {
					space()._add("MINVALUE ")._add(column.getIdentityMinValue());
				}
				if (column.getIdentityMaxValue() != null) {
					space()._add("MAXVALUE ")._add(column.getIdentityMaxValue());
				}
				if (column.isIdentityCycle()) {
					space()._add("CYCLE");
				}
			});
		}
		return instance();
	}

	@Override
	public HsqlSqlBuilder clone() {
		return (HsqlSqlBuilder) super.clone();
	}
}
