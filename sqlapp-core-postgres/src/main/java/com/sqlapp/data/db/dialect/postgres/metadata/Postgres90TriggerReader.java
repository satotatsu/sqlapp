/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

/**
 * Postgresのトリガー読み込み
 * 
 * @author satoh
 * 
 */
public class Postgres90TriggerReader extends PostgresTriggerReader {

	protected Postgres90TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger obj = super.createTrigger(rs);
		obj.setSchemaName(getString(rs, "trigger_schema"));
		obj.setActionOrientation(getString(rs, "action_orientation"));
		obj.setActionTiming(getString(rs, "condition_timing"));
		obj.setStatement(getString(rs, "action_statement"));
		obj.addEventManipulation(getString(rs, "is_insert"));
		obj.addEventManipulation(getString(rs, "is_update"));
		obj.addEventManipulation(getString(rs, "is_delete"));
		obj.setRemarks(getString(rs, "remarks"));
		obj.setWhen(getString(rs, "tgqual"));
		return obj;
	}
}
