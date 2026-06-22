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

package com.sqlapp.data.db.command.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

public class UpdateDictionariesCommandTest2 {

	protected File testProjectDir;

	@Test
	public void testRun() {
		testRun("csv");
		testRun("xlsx");
		testRun("yaml");
		testRun("json");
	}

	private Properties testRun(String fileType) {
		UpdateDictionariesCommand command = new UpdateDictionariesCommand();
		command.setWithSchema((o) -> true);
		command.setTargetFile(new File("src/test/resources/schemas/Catalog.xml"));
		File directory = new File("src/test/resources/html/" + fileType);
		command.setDirectory(directory);
		command.setFileType(fileType);
		command.setDryRun(true);
		command.run();
		Properties properties = command.getPropertiesMap().get(MenuDefinition.Columns);
		System.out.println("================" + fileType + "================\"");
		assertEquals("製品名Active", properties.get("PUBLIC.PRODUCTS.ACTIVE.displayName"));
		assertEquals("製品名Activeコメント", properties.get("PUBLIC.PRODUCTS.ACTIVE.displayRemarks"));
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
			System.out.println(entry.getKey() + "=" + entry.getValue());
		}
		return properties;
	}
}
