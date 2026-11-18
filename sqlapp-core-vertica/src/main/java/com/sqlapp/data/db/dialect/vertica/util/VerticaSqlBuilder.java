/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.vertica.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DB2用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class VerticaSqlBuilder extends AbstractSqlBuilder<VerticaSqlBuilder> {

	public VerticaSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override protected VerticaSqlBuilder typeDefinition(com.sqlapp.data.schemas.Column column) {
		if (column.getArrayDimension() == 0 || column.getDataType().getMetaTtype() != com.sqlapp.data.db.datatype.MetaType.INTERVAL)
			return super.typeDefinition(column);
		int dimensions = column.getArrayDimension();
		String declaration = column.getSpecifics().get(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS);
		String[] bounds = declaration == null ? new String[dimensions] : declaration.split(",",-1);
		if(bounds.length != dimensions) throw new IllegalArgumentException("Interval array bounds do not match dimensions");
		_add("ARRAY[".repeat(dimensions));
		super.typeDefinition(column.clone().setArrayDimension(0).setDataTypeName(null));
		for(String bound:bounds) {
			if(bound != null && !bound.equals("*")) {
				if(!bound.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid Vertica interval array bound");
				_add(",")._add(bound);
			}
			_add("]");
		}
		String capacity=column.getSpecifics().get("vertica.interval.arrayMaxBytes");
		if(capacity!=null) {
			if(!capacity.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid Vertica ARRAY capacity");
			_add("(")._add(capacity)._add(")");
		}
		return this;
	}

	@Override
	public VerticaSqlBuilder definition(com.sqlapp.data.schemas.Column column, boolean withRemarks) {
		if (!column.isIdentity())
			return super.definition(column, withRemarks);
		// Vertica IDENTITY replaces the data type rather than following BIGINT.
		autoIncrement(column);
		notNullDefinition(column);
		if (!com.sqlapp.util.CommonUtils.isEmpty(column.getCheck()))
			checkConstraintDefinition(column);
		if (withRemarks && !com.sqlapp.util.CommonUtils.isEmpty(column.getRemarks()))
			comment(column);
		return this;
	}

	@Override
	public VerticaSqlBuilder argument(com.sqlapp.data.schemas.NamedArgument argument) {
		argumentDirection(argument);
		if (argument.getName() != null)
			name(argument.getName()).space();
		typeDefinition(argument);
		return this;
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected VerticaSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		identity();
		final Long start = column.getIdentityStartValue();
		final Long step = column.getIdentityStep();
		final Integer cache = column.getIdentityCacheSize();
		if (start != null || step != null) {
			space()._add('(')._add(start != null ? start : 1L).comma().space()._add(step != null ? step : 1L);
			if (cache != null) {
				comma().space()._add(cache);
			}
			_add(')');
		} else if (cache != null) {
			space()._add('(')._add(cache)._add(')');
		}
		return this;
	}

	@Override
	public VerticaSqlBuilder clone() {
		return (VerticaSqlBuilder) super.clone();
	}

}
