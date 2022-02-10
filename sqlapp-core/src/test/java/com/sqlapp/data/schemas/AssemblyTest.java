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

import java.io.UnsupportedEncodingException;

import com.sqlapp.data.schemas.Assembly.PermissionSet;
import com.sqlapp.util.BinaryUtils;

public class AssemblyTest extends AbstractDbObjectTest<Assembly> {

	public static Assembly getAssembly() {
		Assembly assembly = new Assembly("AssemblyA");
		AssemblyFile assemblyFile = new AssemblyFile("Aaa.cs");
		try {
			assemblyFile.setContent("sourceA".getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		assembly.setPermissionSet(PermissionSet.Safe);
		assembly.getAssemblyFiles().add(assemblyFile);
		//
		assemblyFile = new AssemblyFile("Bbb.pdb");
		assemblyFile.setContent(BinaryUtils.toBinary(128L));
		assembly.getAssemblyFiles().add(assemblyFile);
		return assembly;
	}

	@Override
	protected Assembly getObject() {
		return getAssembly();
	}

	@Override
	protected void testDiffString(Assembly obj1, Assembly obj2) {
		obj2.setName("b");
		try {
			obj2.getAssemblyFiles().get(0)
					.setContent("sourceB".getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
