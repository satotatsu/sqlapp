/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class SpannerSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public SpannerSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Catalog.class, SqlType.ALTER, SpannerAlterCatalogFactory.class);
		registerSqlFactory(Role.class, SqlType.CREATE, SpannerCreateRoleFactory.class);
		registerSqlFactory(Role.class, SqlType.DROP, SpannerDropRoleFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.GRANT, SpannerGrantRoleFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.REVOKE, SpannerRevokeRoleFactory.class);
		registerSqlFactory(RoleMember.class, State.Added, SqlType.GRANT);
		registerSqlFactory(RoleMember.class, State.Deleted, SqlType.REVOKE);
		registerSqlFactory(Schema.class, SqlType.CREATE, SpannerCreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.DROP, SpannerDropSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER, SpannerAlterSchemaFactory.class);
		registerSqlFactory(Synonym.class, SqlType.CREATE, SpannerCreateSynonymFactory.class);
		registerSqlFactory(Synonym.class, SqlType.DROP, SpannerDropSynonymFactory.class);
		registerSqlFactory(Synonym.class, SqlType.ALTER, SpannerAlterSynonymFactory.class);

		registerSqlFactory(Table.class, SqlType.CREATE, SpannerCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, SpannerDropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, SpannerAlterTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.CheckConstraint.class, SqlType.CREATE,
				SpannerCreateCheckConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ForeignKeyConstraint.class, SqlType.CREATE,
				SpannerCreateForeignKeyConstraintFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, SpannerCreateIndexFactory.class);
		registerSqlFactory(Index.class, SqlType.DROP, SpannerDropIndexFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, SpannerCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, SpannerDropSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, SpannerAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, State.Modified, SqlType.ALTER);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, SpannerSequenceNextValuesFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, SpannerCreateViewFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, SpannerDropViewFactory.class);
		registerSqlFactory(View.class, SqlType.ALTER, SpannerAlterViewFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, SpannerCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE, SpannerTruncateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, SpannerTruncateTemporaryTableFactory.class);
	}

}
