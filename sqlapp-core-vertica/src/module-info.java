/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

module com.sqlapp.core.vertica {
	requires java.xml;
	requires java.sql;
	requires com.sqlapp.core;
	exports com.sqlapp.data.db.dialect.vertica;
	exports com.sqlapp.data.db.dialect.vertica.bulk;
	exports com.sqlapp.data.db.dialect.vertica.metadata;
	exports com.sqlapp.data.db.dialect.vertica.resolver;
	provides com.sqlapp.jdbc.bulk.BulkInsertProvider with
			com.sqlapp.data.db.dialect.vertica.bulk.VirticaBulkInsertProvider;
	provides com.sqlapp.jdbc.bulk.BulkUpsertProvider with
			com.sqlapp.data.db.dialect.vertica.bulk.VirticaBulkUpsertProvider;
	provides com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider with
			com.sqlapp.data.db.dialect.vertica.bulk.VirticaSetBasedMigrationSnapshotProvider;
}
