/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateFunctionFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

/** Requires body and return metadata, or a complete executable definition. */
public class MariadbCreateFunctionFactory extends MySqlCreateFunctionFactory {
	@Override
	protected void addCreateObject(Function function, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(function.getDefinition())
				&& function.getDefinition().stream().allMatch(s -> s == null || s.isBlank())) {
			throw new IllegalArgumentException(
					"MariaDB function CREATE definition must not be blank: " + function.getName());
		}
		if (CommonUtils.isEmpty(function.getDefinition())) {
			if (CommonUtils.isEmpty(function.getStatement())
					|| function.getStatement().stream().allMatch(s -> s == null || s.isBlank())) {
				throw new IllegalArgumentException(
						"MariaDB function requires body or complete CREATE definition; check metadata privileges: "
								+ function.getName());
			}
			var returning = function.getReturning();
			if (!CommonUtils.isEmpty(returning.getDefinition())
					&& returning.getDefinition().stream().allMatch(s -> s == null || s.isBlank())) {
				throw new IllegalArgumentException(
						"MariaDB function return definition must not be blank: " + function.getName());
			}
			if (returning.getDataType() == null
					&& (returning.getDataTypeName() == null || returning.getDataTypeName().isBlank())
					&& CommonUtils.isEmpty(returning.getDefinition())) {
				throw new IllegalArgumentException(
						"MariaDB function requires return type or complete CREATE definition: " + function.getName());
			}
		}
		super.addCreateObject(function, builder);
	}
}
