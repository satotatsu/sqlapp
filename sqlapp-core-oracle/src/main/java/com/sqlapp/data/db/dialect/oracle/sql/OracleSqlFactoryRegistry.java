/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Dimension;
import com.sqlapp.data.schemas.Directory;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeBody;
import com.sqlapp.data.schemas.UniqueConstraint;

public class OracleSqlFactoryRegistry extends
		SimpleSqlFactoryRegistry {

	public OracleSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		//Schema
		registerSqlFactory(Schema.class, SqlType.CREATE,
				OracleCreateSchemaFactory.class);
		//Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				OracleCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				OracleAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE_BY_PK,
				OracleMergeByPkTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK,
				OracleLockTableFactory.class);
		//Index
		registerSqlFactory(Index.class, SqlType.CREATE,
				OracleCreateIndexFactory.class);
		//ForeignKeyConstraint
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				OracleCreateForeignKeyConstraintFactory.class);
		//UniqueConstraint
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				OracleCreateUniqueConstraintFactory.class);
		//CheckConstraint
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE,
				OracleCreateCheckConstraintFactory.class);
		//Partitioning
		registerSqlFactory(Partitioning.class, SqlType.CREATE,
				OracleCreatePartitioningFactory.class);
		//Sequence
		registerSqlFactory(Sequence.class, SqlType.CREATE,
				OracleCreateSequenceFactory.class);
		//Synonym
		registerSqlFactory(Synonym.class, SqlType.CREATE,
				OracleCreateSynonymFactory.class);
		//Public Synonym
		registerSqlFactory(PublicSynonym.class, SqlType.CREATE,
				OracleCreatePublicSynonymFactory.class);
		//Dimension
		registerSqlFactory(Dimension.class, SqlType.CREATE,
				OracleCreateDimensionFactory.class);
		//Function
		registerSqlFactory(Function.class, SqlType.CREATE,
				OracleCreateFunctionFactory.class);
		//Procedure
		registerSqlFactory(Procedure.class, SqlType.CREATE,
				OracleCreateProcedureFactory.class);
		//Trigger
		registerSqlFactory(Trigger.class, SqlType.CREATE,
				OracleCreateTriggerFactory.class);
		//Trigger
		registerSqlFactory(Trigger.class, SqlType.ALTER,
				OracleAlterTriggerFactory.class);
		//Package
		registerSqlFactory(Package.class, SqlType.CREATE,
				OracleCreatePackageFactory.class);
		registerSqlFactory(Package.class, SqlType.DROP,
				OracleDropPackageFactory.class);
		//Package Body
		registerSqlFactory(PackageBody.class, SqlType.CREATE,
				OracleCreatePackageBodyFactory.class);
		registerSqlFactory(PackageBody.class, SqlType.DROP,
				OracleDropPackageBodyFactory.class);
		//
		//Domain
		registerSqlFactory(Domain.class, SqlType.CREATE,
				OracleCreateDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.DROP,
				OracleDropDomainFactory.class);
		//TYPE
		registerSqlFactory(Type.class, SqlType.CREATE,
				OracleCreateTypeFactory.class);
		registerSqlFactory(Type.class, SqlType.DROP,
				OracleDropTypeFactory.class);
		//TYPE BODY
		registerSqlFactory(TypeBody.class, SqlType.CREATE,
				OracleCreateTypeBodyFactory.class);
		registerSqlFactory(TypeBody.class, SqlType.DROP,
				OracleDropTypeBodyFactory.class);
		//Mview
		registerSqlFactory(Mview.class, SqlType.CREATE,
				OracleCreateMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.REFRESH,
				OracleRefreshMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.DROP,
				OracleDropMviewFactory.class);
		//Mview Log
		registerSqlFactory(MviewLog.class, SqlType.CREATE,
				OracleCreateMviewLogFactory.class);
		registerSqlFactory(MviewLog.class, SqlType.DROP,
				OracleDropMviewLogFactory.class);
		//Directory
		registerSqlFactory(Directory.class, SqlType.CREATE,
				OracleCreateDirectoryFactory.class);
		// Row
		registerRowSqlFactory(SqlType.MERGE_ROW, OracleMergeRowFactory.class);
		registerRowSqlFactory(SqlType.INSERT_ROW, OracleInsertRowFactory.class);
	}
}
