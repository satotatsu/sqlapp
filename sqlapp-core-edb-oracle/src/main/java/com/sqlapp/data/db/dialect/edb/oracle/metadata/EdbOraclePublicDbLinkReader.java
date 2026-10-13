/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;
import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PublicDbLinkReader;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;

/** Passwords and user mappings are deliberately not selected. */
public class EdbOraclePublicDbLinkReader extends PublicDbLinkReader {
	protected EdbOraclePublicDbLinkReader(Dialect dialect) {super(dialect);}
	@Override protected List<PublicDbLink> doGetAll(Connection connection,ParametersContext context,ProductVersionInfo version) {
		return EdbOracleCompatibilityCatalog.read(connection,"all_db_links","owner,db_link,type,username,host",
				"owner = 'PUBLIC'",context,Map.of("dbLinkName","db_link"),rows -> {
					var link=new PublicDbLink(rows.getString("db_link"));
					
					link.setUserId(rows.getString("username"));
					link.setDataSource(rows.getString("host"));
					link.getSpecifics().put("edb.dbLink.remoteType",rows.getString("type"));
					return link;
				});
	}
}
