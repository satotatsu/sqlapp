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
			return CockroachMetadata.matches(context,"catalogName",name) ? List.of(new Catalog(name)) : List.of();
		} catch(SQLException e) { throw new IllegalStateException(e); }
	}
}

