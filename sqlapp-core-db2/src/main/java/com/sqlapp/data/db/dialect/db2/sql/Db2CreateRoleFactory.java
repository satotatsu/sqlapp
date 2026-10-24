/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.Role;

public class Db2CreateRoleFactory extends AbstractCreateNamedObjectFactory<Role, Db2SqlBuilder> {
	@Override
	protected void addCreateObject(Role role, Db2SqlBuilder builder) {
		builder.create().space()._add("ROLE").space().name(role.getName());
	}
}
