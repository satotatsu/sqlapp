/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
public class CockroachCatalogReader extends CatalogReader {
	public CockroachCatalogReader(Dialect dialect) { super(dialect); }
	@Override protected SchemaReader newSchemaReader() { return new CockroachSchemaReader(getDialect()); }
	@Override protected TableSpaceReader newTableSpaceReader() { return null; }
	@Override protected DirectoryReader newDirectoryReader() { return null; }
	@Override protected PartitionFunctionReader newPartitionFunctionReader() { return null; }
	@Override protected PartitionSchemeReader newPartitionSchemeReader() { return null; }
	@Override protected AssemblyReader newAssemblyReader() { return null; }
	@Override protected PublicDbLinkReader newPublicDbLinkReader() { return null; }
	@Override protected PublicSynonymReader newPublicSynonymReader() { return null; }
	@Override protected UserReader newUserReader() { return null; }
	@Override protected RoleReader newRoleReader() { return null; }
	@Override protected ObjectPrivilegeReader newObjectPrivilegeReader() { return null; }
	@Override protected RoutinePrivilegeReader newRoutinePrivilegeReader() { return null; }
	@Override protected ColumnPrivilegeReader newColumnPrivilegeReader() { return null; }
	@Override protected UserPrivilegeReader newUserPrivilegeReader() { return null; }
	@Override protected RoleMemberReader newRoleMemberReader() { return null; }
	@Override protected RolePrivilegeReader newRolePrivilegeReader() { return null; }
	@Override protected SettingReader newSettingReader() { return null; }

	@Override protected List<Catalog> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		try {
			String name=c.getCatalog();
			if(!CockroachMetadata.matches(context,"catalogName",name)) return List.of();
			var catalog=new Catalog(name);var regions=new ArrayList<String>();
			try(var sql=c.createStatement();var rows=sql.executeQuery("SHOW REGIONS FROM DATABASE "+CockroachMetadata.quote(name))) {
				while(rows.next()) {
					String region=rows.getString("region");regions.add(region);
					if(rows.getBoolean("primary")) catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION",region);
					if(rows.getBoolean("secondary")) catalog.getSpecifics().put("COCKROACH_SECONDARY_REGION",region);
				}
			}
			if(!regions.isEmpty()) {
				Collections.sort(regions);catalog.getSpecifics().put("COCKROACH_REGIONS",com.sqlapp.util.JsonUtils.toJsonString(regions));
				try(var sql=c.createStatement();var rows=sql.executeQuery("SHOW SURVIVAL GOAL FROM DATABASE "+CockroachMetadata.quote(name))) {
					if(!rows.next()) throw new SQLException("Missing CockroachDB survival goal");
					catalog.getSpecifics().put("COCKROACH_SURVIVAL_GOAL",rows.getString("survival_goal").toUpperCase(Locale.ROOT));
				}
			}
			return List.of(catalog);
		} catch(SQLException e) { throw new IllegalStateException(e); }
	}
}

