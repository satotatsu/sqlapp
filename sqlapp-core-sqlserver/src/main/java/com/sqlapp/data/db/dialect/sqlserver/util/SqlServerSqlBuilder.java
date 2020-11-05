/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.NamedArgumentCollection;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class SqlServerSqlBuilder extends
		AbstractSqlBuilder<SqlServerSqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3976029895381266407L;

	public SqlServerSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	public SqlServerSqlBuilder identity() {
		appendElement("IDENTITY");
		return instance();
	}
	
	public SqlServerSqlBuilder include() {
		appendElement("INCLUDE");
		return instance();
	}

	public SqlServerSqlBuilder identityInsert() {
		appendElement("IDENTITY_INSERT");
		return instance();
	}

	public SqlServerSqlBuilder permissionSet() {
		appendElement("PERMISSION_SET");
		return instance();
	}

	public SqlServerSqlBuilder aggregate() {
		appendElement("AGGREGATE");
		return instance();
	}

	public SqlServerSqlBuilder readonly() {
		appendElement("READONLY");
		return instance();
	}

	public SqlServerSqlBuilder scheme() {
		appendElement("SCHEME");
		return instance();
	}

	public SqlServerSqlBuilder range() {
		appendElement("RANGE");
		return instance();
	}

	public SqlServerSqlBuilder enable() {
		appendElement("ENABLE");
		return instance();
	}

	public SqlServerSqlBuilder owner() {
		appendElement("OWNER");
		return instance();
	}
	
	public SqlServerSqlBuilder caller() {
		appendElement("CALLER");
		return instance();
	}
	
	
	public SqlServerSqlBuilder disable() {
		appendElement("DISABLE");
		return instance();
	}

	public SqlServerSqlBuilder changeTracking() {
		appendElement("CHANGE_TRACKING");
		return instance();
	}

	public SqlServerSqlBuilder tablock() {
		appendElement("TABLOCK");
		return instance();
	}

	public SqlServerSqlBuilder holdlock() {
		appendElement("HOLDLOCK");
		return instance();
	}
	
	public SqlServerSqlBuilder updlock() {
		appendElement("UPDLOCK");
		return instance();
	}

	public SqlServerSqlBuilder filetable() {
		appendElement("FILETABLE");
		return instance();
	}

	public SqlServerSqlBuilder filetableDirectory() {
		appendElement("FILETABLE_DIRECTORY");
		return instance();
	}

	public SqlServerSqlBuilder filetableCollateFilename() {
		appendElement("FILETABLE_COLLATE_FILENAME");
		return instance();
	}

	public SqlServerSqlBuilder filetableStreamidUniqueConstraintName() {
		appendElement("FILETABLE_STREAMID_UNIQUE_CONSTRAINT_NAME");
		return instance();
	}

	public SqlServerSqlBuilder filetableFullpathUniqueConstraintName() {
		appendElement("FILETABLE_FULLPATH_UNIQUE_CONSTRAINT_NAME");
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
	public SqlServerSqlBuilder arguments(
			NamedArgumentCollection<?> arguments) {
		if (arguments.getParent() instanceof Procedure) {
			return arguments("\n\t", arguments, "", "\n\t, ");
		} else {
			return arguments("(", arguments, ")", ", ");
		}
	}

	@Override
	public SqlServerSqlBuilder clone(){
		return (SqlServerSqlBuilder)super.clone();
	}
}
