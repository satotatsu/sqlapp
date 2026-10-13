/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import java.sql.Connection;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PackageBodyReader;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.parameter.ParametersContext;
public class EdbOraclePackageBodyReader extends PackageBodyReader {
	protected EdbOraclePackageBodyReader(Dialect dialect) { super(dialect); }
	@Override protected List<PackageBody> doGetAll(Connection connection,ParametersContext context,ProductVersionInfo version) {
		return EdbOraclePackageSource.read(connection,context,true).stream().map(source -> {
			var object=new PackageBody(source.name());object.setSchemaName(source.schema());object.setDialect(getDialect());
			if(getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement()) object.setDefinition(EdbOraclePackageSource.definition(getDialect(),source,true));
			return object;
		}).toList();
	}
}
