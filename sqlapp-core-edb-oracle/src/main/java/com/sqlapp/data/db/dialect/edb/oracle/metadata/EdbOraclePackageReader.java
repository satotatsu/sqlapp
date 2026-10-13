/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import java.sql.Connection;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.parameter.ParametersContext;
public class EdbOraclePackageReader extends PackageReader {
	protected EdbOraclePackageReader(Dialect dialect) { super(dialect); }
	@Override protected List<Package> doGetAll(Connection connection,ParametersContext context,ProductVersionInfo version) {
		return EdbOraclePackageSource.read(connection,context,false).stream().map(source -> {
			var object=new Package(source.name());object.setSchemaName(source.schema());object.setDialect(getDialect());
			if(getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement()) object.setDefinition(EdbOraclePackageSource.definition(getDialect(),source,false));
			return object;
		}).toList();
	}
}
