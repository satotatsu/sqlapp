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

package com.sqlapp.data.db.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.test.AbstractTest;
import com.sqlapp.data.schemas.rowiterator.DataFormat;

public class ConvertDataCommandTest extends AbstractTest {

	@Test
	public void testRun(@TempDir File directoryPath) throws ParseException, IOException, SQLException {
		File outputDir = new File(directoryPath, "output");
		outputDir.mkdirs();
		ConvertDataCommand command = new ConvertDataCommand();
		command.setDirectory(directoryPath);
		command.setOutputFileType(DataFormat.EXCEL);
		command.setOutputDirectory(outputDir);
		command.run();
	}

}
