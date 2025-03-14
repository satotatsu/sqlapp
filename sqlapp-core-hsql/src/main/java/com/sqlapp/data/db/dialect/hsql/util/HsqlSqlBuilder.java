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
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * HSQL用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class HsqlSqlBuilder extends AbstractSqlBuilder<HsqlSqlBuilder> {

	public HsqlSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected HsqlSqlBuilder autoIncrement(AbstractColumn<?> column) {
		// space().append("GENERATED ALWAYS AS IDENTITY");
		return instance();
	}

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
		if (column.getDataType() == DataType.DOMAIN) {
			this._add(column.getDataTypeName());
		} else {
			typeDefinition(column);
			characterSetDefinition(column);
			collateDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getDefaultValue()) || column.isIdentity()) {
			defaultDefinition(column);
		}
		notNullDefinition(column);
		this.onUpdateDefinition(column);
		if (column.getCheck() != null) {
			checkConstraintDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getRemarks())) {
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
	public HsqlSqlBuilder _fromSysDummy() {
		appendElement("FROM (VALUES(0))");
		return instance();
	}

	@Override
	public HsqlSqlBuilder clone() {
		return (HsqlSqlBuilder) super.clone();
	}
}
