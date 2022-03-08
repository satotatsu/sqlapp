/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;

public class HsqlCreateFunctionFactory extends
		AbstractCreateFunctionFactory<HsqlSqlBuilder> {

	@Override
	protected void addCreateObject(final Function obj, HsqlSqlBuilder builder) {
		builder.create().function();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.space().arguments(obj.getArguments());
		builder.lineBreak().returns().space()._add(obj.getReturning());
		if (obj.getSpecificName() != null) {
			builder.lineBreak().specific();
			builder.name(obj.getSpecificName());
		}
		builder.lineBreak().language().space()._add(obj.getLanguage());
		if (obj.getDeterministic() != null) {
			if (obj.getDeterministic().booleanValue()) {
				builder.lineBreak();
			} else {
				builder.lineBreak().not();
			}
			builder.deterministic();
		}
		if (obj.getSqlDataAccess() != null) {
			builder.lineBreak()._add(obj.getSqlDataAccess());
		}
		if (obj.getSqlSecurity() != null) {
			builder.lineBreak()._add(obj.getSqlSecurity());
		}
		if (obj.getSavepointLevel() != null) {
			builder.lineBreak()._add(obj.getSavepointLevel());
		}
		if (obj.getMaxDynamicResultSets() != null
				&& obj.getMaxDynamicResultSets().intValue() > 0) {
			builder.lineBreak().dynamic().result().sets().space()
					._add(obj.getMaxDynamicResultSets());
		}
		if (!"SQL".equals(obj.getLanguage())) {
			builder.lineBreak().no().sql();
		}
		if (obj.getOnNullCall() != null) {
			builder.lineBreak()._add(obj.getOnNullCall());
		}
		if ("SQL".equals(obj.getLanguage())) {
			builder.lineBreak()._add(obj.getStatement());
		} else {
			String methodFullPath=null;
			if (obj.getClassNamePrefix()!=null){
				methodFullPath=obj.getClassNamePrefix()+":"+obj.getClassName() + "." + obj.getMethodName();
			} else{
				methodFullPath=obj.getClassName() + "." + obj.getMethodName();
			}
			builder.lineBreak()
					.external()
					.name()
					.space()
					.sqlChar(
							methodFullPath);
		}
	}
}
