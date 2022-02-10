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

package com.sqlapp.data.db.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.test.AbstractTest;

public class ConvertDataFileCommandTest extends AbstractTest {
	
	private String directoryPath="./bin/export";
	
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		ConvertDataFileCommand command=new ConvertDataFileCommand();
		command.setDirectory(new File(directoryPath));
		command.setOutputFileType(WorkbookFileType.EXCEL2007);
		command.setOutputDirectory(new File(directoryPath+"2"));
		command.run();
	}


}
