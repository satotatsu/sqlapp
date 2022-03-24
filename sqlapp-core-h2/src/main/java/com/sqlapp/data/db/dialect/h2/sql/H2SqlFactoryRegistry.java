/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

public class H2SqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public H2SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.DROP, H2DropTableFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, H2DropViewFactory.class);
		registerSqlFactory(Trigger.class, SqlType.CREATE,
				H2CreateTriggerFactory.class);
		registerSqlFactory(Function.class, SqlType.CREATE,
				H2CreateFunctionFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				H2CreateSequenceFactory.class);
	}
}
