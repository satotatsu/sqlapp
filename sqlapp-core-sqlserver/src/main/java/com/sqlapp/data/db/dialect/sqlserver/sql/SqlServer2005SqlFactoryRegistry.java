/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

public class SqlServer2005SqlFactoryRegistry extends SqlServerSqlFactoryRegistry {

	public SqlServer2005SqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setDialect(com.sqlapp.data.db.sql.SqlFactory<?> factory, Object source) {
		super.setDialect(factory, source);
		// A hand-authored Schema has the generic default dialect. Keep the explicitly
		// selected SQL Server registry.
		Object sourceDialect = source == null ? null : com.sqlapp.util.SimpleBeanUtils.getValue(source, "dialect");
		if (factory != null && !(sourceDialect instanceof com.sqlapp.data.db.dialect.sqlserver.SqlServer2000))
			factory.setDialect(getDialect());
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE, SqlServer2005CreateCheckConstraintFactory.class);
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				SqlServer2005CreateForeignKeyConstraintFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, SqlServer2005CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, SqlServer2005AlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, SqlServer2005DropTableFactory.class);
		// Index
		registerSqlFactory(Index.class, SqlType.CREATE, SqlServer2005CreateIndexFactory.class);
		//
		registerSqlFactory(Partitioning.class, SqlType.CREATE, SqlServer2005CreatePartitioningFactory.class);
		//
		registerSqlFactory(Assembly.class, SqlType.CREATE, SqlServer2005CreateAssemblyFactory.class);
		//
		registerSqlFactory(Function.class, SqlType.CREATE, SqlServer2005CreateFunctionFactory.class);
		registerSqlFactory(Function.class, SqlType.DROP, SqlServer2005DropFunctionFactory.class);
		//
		registerSqlFactory(Procedure.class, SqlType.CREATE, SqlServer2005CreateProcedureFactory.class);
		// View
		registerSqlFactory(View.class, SqlType.CREATE, SqlServer2005CreateViewFactory.class);
		// Trigger
		registerSqlFactory(Trigger.class, SqlType.CREATE, SqlServer2005CreateTriggerFactory.class);
		//
		registerSqlFactory(PartitionScheme.class, SqlType.CREATE, SqlServer2005CreatePartitionSchemeFactory.class);
		registerSqlFactory(PartitionScheme.class, SqlType.ALTER, SqlServer2005AlterPartitionSchemeFactory.class);
		registerSqlFactory(PartitionScheme.class, SqlType.DROP, SqlServer2005DropPartitionSchemeFactory.class);
		//
		registerSqlFactory(PartitionFunction.class, SqlType.CREATE, SqlServer2005CreatePartitionFunctionFactory.class);
		registerSqlFactory(PartitionFunction.class, SqlType.DROP, SqlServer2005DropPartitionFunctionFactory.class);
		registerSqlFactory(PartitionFunction.class, SqlType.ALTER, SqlServer2005AlterPartitionFunctionFactory.class);
	}

}
