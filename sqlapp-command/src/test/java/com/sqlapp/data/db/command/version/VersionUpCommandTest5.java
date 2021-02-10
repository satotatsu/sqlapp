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

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.OutputTextBuilder;

public class VersionUpCommandTest5 extends AbstractVersionUpCommandTest {
	
	VersionUpCommand command;
	@Override
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final DbVersionFileHandler handler=new DbVersionFileHandler();
		testVersionUp(handler, (times, ds)->{
			final Table table=command.getTable();
			final DbVersionHandler dbVersionHandler=new DbVersionHandler();
			final OutputTextBuilder builder=new OutputTextBuilder();
			dbVersionHandler.append(table, builder);
			final String expected=this.getResource("versionAfter.txt");
		});
	}
	
	@Override
	protected void initialize(final VersionUpCommand command){
		super.initialize(command);
		command.setLastChangeToApply(BASEDATE);
		this.command=command;
	}

}
