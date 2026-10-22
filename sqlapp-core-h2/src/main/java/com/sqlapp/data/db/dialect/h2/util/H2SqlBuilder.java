/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;

/**
 * H2用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class H2SqlBuilder extends AbstractSqlBuilder<H2SqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public H2SqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * ALIAS句を追加します
	 * 
	 */
	public H2SqlBuilder alias() {
		appendElement("ALIAS");
		return instance();
	}

	@Override
	public H2SqlBuilder definition(Column column, boolean withRemarks) {
		typeDefinition(column);
		if (column.isHidden())
			space()._add("INVISIBLE");
		if (!CommonUtils.isEmpty(column.getFormula())) {
			if (column.isIdentity() || !CommonUtils.isEmpty(column.getDefaultValue())
					|| !CommonUtils.isEmpty(column.getOnUpdate()))
				throw new IllegalArgumentException(
						"H2 generated columns cannot also specify identity, default or ON UPDATE: " + column.getName());
			space()._add(
					getDialect() instanceof com.sqlapp.data.db.dialect.h2.H2_200 ? "GENERATED ALWAYS AS (" : "AS (")
					._add(column.getFormula())._add(")");
		} else if (!column.isIdentity())
			defaultDefinition(column);
		if (!CommonUtils.isEmpty(column.getOnUpdate()))
			onUpdateDefinition(column);
		notNullDefinition(column);
		if (column.isIdentity())
			autoIncrement(column);
		if ("true".equals(column.getSpecifics().get("h2.defaultOnNull")))
			space()._add("DEFAULT ON NULL");
		if (!CommonUtils.isEmpty(column.getCheck()))
			checkConstraintDefinition(column);
		if (withRemarks && !CommonUtils.isEmpty(column.getRemarks()))
			space()._add("COMMENT ").sqlChar(column.getRemarks());
		return this;
	}

	@Override
	public H2SqlBuilder definitionForAlterColumn(Column column) {
		definition(column, false);
		if (!column.isNotNull() && !column.isIdentity())
			space()._add("NULL");
		return this;
	}

	@Override
	protected H2SqlBuilder typeDefinition(Column column) {
		if (column.getDomain() != null) {
			var domain = column.getDomain();
			if (domain.getName() == null || domain.getName().isBlank())
				throw new IllegalArgumentException("H2 column domain requires a name");
			if (domain.getSchemaName() != null)
				name(domain.getSchemaName())._add(".");
			name(domain.getName());
		} else if (column.getDataType() == DataType.DOMAIN) {
			// Legacy domain names remain usable; new models should carry a Domain
			// reference.
			if (column.getDataTypeName() == null || column.getDataTypeName().isBlank())
				throw new IllegalArgumentException("H2 column domain requires a name");
			name(column.getDataTypeName());
		} else
			super.typeDefinition(column);
		return this;
	}

	@Override
	protected void onUpdateDefinition(Column column) {
		space()._add("ON UPDATE ")._add(column.getOnUpdate());
	}

	@Override
	protected H2SqlBuilder autoIncrement(AbstractColumn<?> column) {
		space().generated();
		if (column.getIdentityGenerationType() == IdentityGenerationType.ByDefault)
			by().default_();
		else
			always();
		as().identity();
		var parts = new java.util.ArrayList<String>();
		if (column.getIdentityStartValue() != null)
			parts.add("START WITH " + column.getIdentityStartValue());
		if (column.getIdentityLastValue() != null)
			parts.add("RESTART WITH " + column.getIdentityLastValue());
		if (column.getIdentityStep() != null)
			parts.add("INCREMENT BY " + column.getIdentityStep());
		if (column.getIdentityMinValue() != null)
			parts.add("MINVALUE " + column.getIdentityMinValue());
		if (column.getIdentityMaxValue() != null)
			parts.add("MAXVALUE " + column.getIdentityMaxValue());
		if (column.isIdentityCycle())
			parts.add("CYCLE");
		if (column instanceof Column concrete && concrete.getSpecifics().get("h2.identityCache") != null) {
			String cache = concrete.getSpecifics().get("h2.identityCache");
			try {
				if (Long.parseLong(cache) < 0)
					throw new NumberFormatException();
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("H2 identity cache must be a nonnegative integer: " + cache, ex);
			}
			parts.add("CACHE " + cache);
		}
		if (!parts.isEmpty())
			space()._add("(")._add(String.join(" ", parts))._add(")");
		return this;
	}

	@Override
	public H2SqlBuilder clone() {
		return (H2SqlBuilder) super.clone();
	}

}
