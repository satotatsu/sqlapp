/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DbLinkReader;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;

/** Passwords and user mappings are deliberately not selected. */
public class EdbOracleDbLinkReader extends DbLinkReader {
	protected EdbOracleDbLinkReader(Dialect dialect) {super(dialect);}
	@Override protected List<DbLink> doGetAll(Connection connection,ParametersContext context,ProductVersionInfo version) {
		return EdbOracleCompatibilityCatalog.read(connection,"all_db_links","owner,db_link,type,username,host",
				"owner <> 'PUBLIC'",context,Map.of("schemaName","owner","dbLinkName","db_link"),rows -> {
					var link=new DbLink(rows.getString("db_link"));
					link.setSchemaName(rows.getString("owner"));
					link.setUserId(rows.getString("username"));
					link.setDataSource(rows.getString("host"));
					link.getSpecifics().put("edb.dbLink.remoteType",rows.getString("type"));
					return link;
				});
	}
}
