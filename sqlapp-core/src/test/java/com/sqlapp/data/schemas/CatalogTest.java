/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.CommonUtils;

public class CatalogTest extends AbstractDbObjectTest<Catalog> {

	@Override
	protected Catalog getObject() {
		Catalog catalog = getCatalog("CatalogA");
		return catalog;
	}

	public static Catalog getCatalog(String name) {
		Catalog catalog = new Catalog(name);
		catalog.getSchemas().add(SchemaTest.getSchema("SchemaA"));
		catalog.getAssemblies().add(AssemblyTest.getAssembly());
		catalog.getObjectPrivileges().add(ObjectPrivilegeTest.getPrivilege());
		catalog.getTableSpaces().add(TableSpaceTest.getTableSpace());
		catalog.getUserPrivileges().add(UserPrivilegeTest.getPrivilege());
		catalog.getRolePrivileges().add(RolePrivilegeTest.getPrivilege());
		catalog.getRoles().add(RoleTest.getRole());
		catalog.getColumnPrivileges().add(ColumnPrivilegeTest.getColumnPrivilege("colA"));
		catalog.getUsers().add(UserTest.getUser());
		catalog.getPublicSynonyms().add(
				PublicSynonymTest.getSynonym("publicSynonymA"));
		catalog.getSettings().add(SettingTest.getSetting());
		catalog.getPartitionSchemes().add(PartitionSchemeTest.getPartitionScheme("PartitionSchemaA"));
		catalog.getDirectories().add(DirectoryTest.getDirectory("DirecotryA"));
		catalog.setCharacterSemantics(CharacterSemantics.Char);
		catalog.setCharacterSet("utf8");
		catalog.setCollation("utf8_bin");
		return catalog;
	}

	@Override
	protected void testDiffString(Catalog obj1, Catalog obj2) {
	}

	@Test
	public void testApply() {
		Catalog catalog = getCatalog("CatalogA");
		List<DbObject<?>> objects=CommonUtils.list();
		catalog.applyAll(c->objects.add(c));
		assertTrue(objects.size()>80);
	}

}
