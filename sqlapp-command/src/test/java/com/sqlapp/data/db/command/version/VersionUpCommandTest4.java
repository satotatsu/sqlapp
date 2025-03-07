/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.version;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.OutputTextBuilder;

public class VersionUpCommandTest4 extends AbstractVersionUpCommandTest {
	/**
	 * 全部元に戻すテスト
	 * @throws ParseException
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final DbVersionFileHandler handler=new DbVersionFileHandler();
		testVersionUp(handler, (times, ds)->{
			try {
				handler.addUpDownSql((""+BASEDATE+3).toString(), "create table4", "create table DDD (id int primary key, text varchar(10))", "drop table DDD");
				final VersionDownCommand versionDownCommand=new VersionDownCommand();
				initialize(versionDownCommand, ds);
				versionDownCommand.setLastChangeToApply(times.get(times.size()-3)-1);
				versionDownCommand.run();
				final Table table=versionDownCommand.getTable();
				this.replaceAppliedAt(table, DateUtils.parse("20160715123456", "yyyyMMddHHmmss"));
				final DbVersionHandler dbVersionHandler=new DbVersionHandler();
				final OutputTextBuilder builder=new OutputTextBuilder();
				dbVersionHandler.append(table, builder);
				final String expected=this.getResource("versionAfter4.txt");
				assertEquals(expected, builder.toString());
			} catch (final Exception e) {
				throw new RuntimeException(e);
			} finally {
				dropTables(ds, "AAA", "BBB", "CCC", "DDD", "changelog");
			}
		});
	}
}
