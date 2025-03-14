/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

abstract class AbstractTaskTest {

	@TempDir
	File testProjectDir;
	File settingsFile;
	File buildFile;

	@BeforeEach
	public void setup() {
		settingsFile = new File(testProjectDir, "settings.gradle");
		buildFile = new File(testProjectDir, "build.gradle");
	}

	protected void writeFile(File destination, String content) throws IOException {
		BufferedWriter output = null;
		try {
			output = new BufferedWriter(new FileWriter(destination));
			output.write(content);
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}
}
