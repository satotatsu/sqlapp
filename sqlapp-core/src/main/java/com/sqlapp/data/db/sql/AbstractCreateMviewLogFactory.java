/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Create Materialized View Log生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateMviewLogFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<MviewLog, S> {

	@Override
	protected void addCreateObject(final MviewLog obj, S builder) {
		if (isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().materialized().view().log().on();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			if (!CommonUtils.isEmpty(obj.getTableSpaceName())){
				builder.lineBreak()._add(obj.getTableSpaceName());
			}
		}
	}

	@Override
	protected void addOptions(final MviewLog obj, List<SqlOperation> sqlList) {

	}

}
