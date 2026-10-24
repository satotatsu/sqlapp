/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.ISTriggerReader;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

/**
 * MySqlのトリガー作成クラス
 * 
 * @author satoh
 *
 */
public class MySqlTriggerReader extends ISTriggerReader {

	protected MySqlTriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Trigger createTrigger(ExResultSet rs) throws SQLException {
		Trigger trigger = super.createTrigger(rs);
		MySqlDefiner.read(trigger.getSpecifics(), rs.getString("DEFINER"));
		String order = rs.getString("ACTION_ORDER");
		if (order != null) {
			trigger.getSpecifics().put(com.sqlapp.data.db.dialect.mysql.util.MySqlTriggerOrder.ACTION_ORDER, order);
			com.sqlapp.data.db.dialect.mysql.util.MySqlTriggerOrder.order(trigger);
		}
		com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext.read(rs, trigger);
		return trigger;
	}

}
