/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sybase.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.NamedArgumentCollection;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class SybaseSqlBuilder extends
		AbstractSqlBuilder<SybaseSqlBuilder> {

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
	 * @see
	 * com.sqlapp.util.AbstractSqlBuilder#appendArgumentBefore(com.sqlapp.data
	 * .schemas.NamedArgument)
	 */
	@Override
	protected void argumentBefore(NamedArgument obj) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.AbstractSqlBuilder#appendArgumentAfter(com.sqlapp.data
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
	public SybaseSqlBuilder arguments(
			NamedArgumentCollection<?> arguments) {
		if (arguments.getParent() instanceof Procedure) {
			return arguments("\n\t", arguments, "", "\n\t, ");
		} else {
			return arguments("(", arguments, ")", ", ");
		}
	}

	@Override
	public SybaseSqlBuilder clone(){
		return (SybaseSqlBuilder)super.clone();
	}
}
