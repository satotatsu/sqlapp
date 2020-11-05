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
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.OutputTextBuilder;

public class VersionUpCommandTest5 extends AbstractVersionUpCommandTest {
	
	VersionUpCommand command;
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		DbVersionFileHandler handler=new DbVersionFileHandler();
		List<Long> times=testVersionUp(handler);
		Table table=command.getTable();
		DbVersionHandler dbVersionHandler=new DbVersionHandler();
		OutputTextBuilder builder=new OutputTextBuilder();
		dbVersionHandler.append(table, builder);
		String expected=this.getResource("versionAfter.txt");
		//assertEquals(expected, builder.toString());	
	}
	
	protected void initialize(VersionUpCommand command){
		super.initialize(command);
		command.setLastChangeToApply(BASEDATE);
		this.command=command;
	}

}
