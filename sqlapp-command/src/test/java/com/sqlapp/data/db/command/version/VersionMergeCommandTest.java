/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
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

public class VersionMergeCommandTest extends AbstractVersionUpCommandTest {
	/**
	 * デフォルトで1つ元に戻すテスト
	 * @throws ParseException
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final DbVersionFileHandler handler=new DbVersionFileHandler();
		final VersionUpCommand versionUpCommand=createVersionUpCommand();
		testVersionUp(versionUpCommand, handler, (times, ds)->{
			try {
				versionUpCommand.deleteVersion(versionUpCommand.getTable(), 20160603124532123L);
				dropTables(ds, "BBB");
				final VersionMergeCommand command=new VersionMergeCommand();
				initialize(command, ds);
				command.run();
				final Table table=command.getTable();
				this.replaceAppliedAt(table, DateUtils.parse("20160715123456", "yyyyMMddHHmmss"));
				final DbVersionHandler dbVersionHandler=new DbVersionHandler();
				final OutputTextBuilder builder=new OutputTextBuilder();
				dbVersionHandler.append(table, builder);
				final String expected=this.getResource("versionMergeAfter1.txt");
				assertEquals(expected, builder.toString());
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		});
	}
}
