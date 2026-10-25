/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.Dialect;

public class Db2_1110SqlFactoryRegistry extends Db2_1050SqlFactoryRegistry {

	public Db2_1110SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, com.sqlapp.data.db.sql.SqlType.ALTER,
				Db2AlterIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, com.sqlapp.data.schemas.State.Modified,
				com.sqlapp.data.db.sql.SqlType.ALTER);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, com.sqlapp.data.db.sql.SqlType.DROP,
				Db2DropIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, com.sqlapp.data.db.sql.SqlType.CREATE,
				Db2_1110CreateSynonymFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, com.sqlapp.data.db.sql.SqlType.DROP,
				Db2_1110DropSynonymFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, com.sqlapp.data.db.sql.SqlType.ALTER,
				Db2AlterSynonymFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, com.sqlapp.data.schemas.State.Modified,
				com.sqlapp.data.db.sql.SqlType.ALTER);
		registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, com.sqlapp.data.db.sql.SqlType.CREATE,
				Db2_1110CreateSequenceFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, com.sqlapp.data.db.sql.SqlType.DROP,
				Db2DropSequenceFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, com.sqlapp.data.db.sql.SqlType.ALTER,
				Db2AlterSequenceFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, com.sqlapp.data.schemas.State.Modified,
				com.sqlapp.data.db.sql.SqlType.ALTER);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, com.sqlapp.data.db.sql.SqlType.CREATE,
				Db2CreateTriggerFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, com.sqlapp.data.db.sql.SqlType.DROP,
				Db2DropTriggerFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, com.sqlapp.data.db.sql.SqlType.ALTER,
				Db2AlterTriggerFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, com.sqlapp.data.schemas.State.Modified,
				com.sqlapp.data.db.sql.SqlType.ALTER);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, com.sqlapp.data.db.sql.SqlType.CREATE,
				Db2CreateViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, com.sqlapp.data.db.sql.SqlType.DROP,
				Db2DropViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, com.sqlapp.data.db.sql.SqlType.ALTER,
				Db2AlterViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, com.sqlapp.data.schemas.State.Modified,
				com.sqlapp.data.db.sql.SqlType.ALTER);
	}

}
