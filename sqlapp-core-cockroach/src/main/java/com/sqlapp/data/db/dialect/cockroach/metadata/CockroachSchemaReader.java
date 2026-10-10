/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
public class CockroachSchemaReader extends SchemaReader {
	public CockroachSchemaReader(Dialect dialect) { super(dialect); }
	@Override protected TableReader newTableReader() { return new CockroachTableReader(getDialect()); }
	@Override protected ViewReader newViewReader() { return new CockroachViewReader(getDialect()); }
	@Override protected MviewReader newMviewReader() { return null; }
	@Override protected MviewLogReader newMviewLogReader() { return null; }
	@Override protected SequenceReader newSequenceReader() { return new CockroachSequenceReader(getDialect()); }
	@Override protected DbLinkReader newDbLinkReader() { return null; }
	@Override protected DomainReader newDomainReader() { return null; }
	@Override protected TypeReader newTypeReader() { return new CockroachTypeReader(getDialect()); }
	@Override protected TypeBodyReader newTypeBodyReader() { return null; }
	@Override protected SynonymReader newSynonymReader() { return null; }
	@Override protected TableLinkReader newTableLinkReader() { return null; }
	@Override protected RuleReader newRuleReader() { return null; }
	@Override protected FunctionReader newFunctionReader() { return new CockroachFunctionReader(getDialect()); }
	@Override protected ProcedureReader newProcedureReader() { return null; }
	@Override protected PackageReader newPackageReader() { return null; }
	@Override protected PackageBodyReader newPackageBodyReader() { return null; }
	@Override protected ConstantReader newConstantReader() { return null; }
	@Override protected TriggerReader newTriggerReader() { return null; }
	@Override protected XmlSchemaReader newXmlSchemaReader() { return null; }
	@Override protected OperatorReader newOperatorReader() { return null; }
	@Override protected OperatorClassReader newOperatorClassReader() { return null; }
	@Override protected ExternalTableReader newExternalTableReader() { return null; }
	@Override protected DimensionReader newDimensionReader() { return null; }
	@Override protected EventReader newEventReader() { return null; }
	@Override protected MaskReader newMaskReader() { return null; }

	@Override protected List<Schema> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result=new ArrayList<Schema>();
		try(var sql=c.createStatement(); var rows=sql.executeQuery("SELECT schema_name FROM information_schema.schemata WHERE catalog_name=current_database() ORDER BY schema_name")) {
			while(rows.next()) {
				String name=rows.getString(1);
				if(CockroachMetadata.userSchema(name) && CockroachMetadata.matches(context,"schemaName",name))
					result.add(new Schema(name).setCatalogName(c.getCatalog()));
			}
		} catch(SQLException e) { throw new IllegalStateException("Cannot read CockroachDB schemas",e); }
		return result;
	}
}

