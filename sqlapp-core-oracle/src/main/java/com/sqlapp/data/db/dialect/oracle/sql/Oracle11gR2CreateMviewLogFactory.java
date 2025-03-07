/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateMviewLogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

public class Oracle11gR2CreateMviewLogFactory extends
		AbstractCreateMviewLogFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final MviewLog obj, OracleSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().materialized().view().log().on();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			if (!CommonUtils.isEmpty(obj.getTableSpaceName())){
				builder.lineBreak()._add(obj.getTableSpaceName());
			}
			builder.lineBreak();
			builder.with();
			boolean added=false;
			if (obj.isSaveObjectId()){
				builder.comma(added);
				builder.object().id();
				added=true;
			}
			if (obj.isSavePrimaryKey()){
				builder.comma(added);
				builder.primaryKey();
				added=true;
			}
			if (obj.isSaveRowIds()){
				builder.comma(added);
				builder.rowid();
				added=true;
			}
			if (obj.isSaveSequence()){
				builder.comma(added);
				builder.sequence();
				added=true;
			}
			if (obj.isCommitScnBased()){
				builder.comma(added);
				builder.commit().scn();
				added=true;
			}
			if (!CommonUtils.isEmpty(obj.getColumns())){
				builder.lineBreak();
				builder.names(obj.getColumns());
			}
			if (obj.isIncludeNewValues()){
				builder.lineBreak();
				builder.including()._new().values();
			}
			builder.lineBreak();
			builder.purge();
			if (obj.isPurgeDeferred()){
				builder.start().with().space()._add(DateUtils.format(obj.getPurgeStart()));
				if (!CommonUtils.isEmpty(obj.getPurgeInterval())){
					builder.space().repeat()._add(obj.getPurgeInterval());
				} else{
					//builder.space().next().space()._add(DateUtils.format(obj.getPurgeStart()));
				}
			} else{
				builder.immediate();
				if (obj.isPurgeAsynchronous()){
					builder.asynchronous();
				} else{
					builder.synchronous();
				}
			}
		}
	}

	@Override
	protected void addOptions(final MviewLog obj, List<SqlOperation> sqlList) {

	}
}
