/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;

public class EdbOracleSynonymReader extends SynonymReader {
	protected EdbOracleSynonymReader(Dialect dialect) {super(dialect);}
	@Override protected List<Synonym> doGetAll(Connection connection,ParametersContext context,ProductVersionInfo version) {
		return EdbOracleCompatibilityCatalog.read(connection,"all_synonyms",
				"schema_name,synonym_name,table_schema_name,table_name,db_link","owner <> 'PUBLIC'",
				context,Map.of("schemaName","schema_name","synonymName","synonym_name"),rows -> {
					var synonym=new Synonym(rows.getString("synonym_name"));
					synonym.setSchemaName(rows.getString("schema_name"));
					synonym.setObjectSchemaName(rows.getString("table_schema_name"));
					synonym.setObjectName(rows.getString("table_name"));
					synonym.setDbLinkName(rows.getString("db_link"));
					return synonym;
				});
	}
}
