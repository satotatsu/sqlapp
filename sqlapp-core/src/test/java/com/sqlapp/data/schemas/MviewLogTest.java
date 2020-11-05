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

public class MviewLogTest extends AbstractDbObjectTest<MviewLog>{

	public static MviewLog getMviewLog(String name){
		MviewLog mviewLog=new MviewLog(name);
		mviewLog.setSavePrimaryKey(true).setSaveObjectId(true);
		mviewLog.setPurgeStart(toTimestamp("2011-02-01 22:30:35"));
		mviewLog.setMasterTableName("MasterTable");
		mviewLog.getColumns().add("ACol");
		mviewLog.getColumns().add("BCol");
		mviewLog.setRemarks("コメント");
		return mviewLog;
	}

	@Override
	protected MviewLog getObject() {
		return getMviewLog("MviewLogName");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<MviewLog> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(MviewLog obj1, MviewLog obj2) {
		obj2.setName("b");
		obj2.getColumns().add("CCol");
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
