/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-gradle-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.gradle.plugins

import javax.sql.DataSource
import com.sqlapp.data.db.command.export.ExportData2FileCommand
import com.sqlapp.data.db.sql.Options
import org.gradle.api.Plugin
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.testfixtures.ProjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sqlapp.gradle.plugins.pojo.*;
import com.zaxxer.hikari.HikariConfig
import org.junit.jupiter.api.Test;

class ExportXmlTaskTest {
	@Test
    public void testExec() {
        Project project = ProjectBuilder.builder().build();
		ExportXmlTask task =project.tasks.create('exportXml', ExportXmlTask);
		project.extensions.create('exportXml', ExportXmlPojo, project);
		project.extensions.exportXml.dataSource {
			driverClassName "org.hsqldb.jdbc.JDBCDriver"
			url "jdbc:hsqldb:./bin/tmp"
			username "root"
			password "password"
		}
		project.extensions.exportXml.schemaOptions {
			tableOptions {
				
			}
		}
        assertEquals("org.hsqldb.jdbc.JDBCDriver", project.extensions.exportXml.dataSource.driverClassName)
		//task.exec()
    }

	@Test
	public void testExecProp() {
		ProjectBuilder projectBuilder=ProjectBuilder.builder();
		projectBuilder.withProjectDir(new File("./"));
		projectBuilder.getProperties().put("envPath", "./src/test/environment/default");
		Project project = projectBuilder.build();
		ExportXmlTask task =project.tasks.create('exportXml', ExportXmlTask);
		project.extensions.create('exportXml', ExportXmlPojo, project);
		project.extensions.exportXml.dataSource {
			properties "./src/test/resources/test_ds.properties"
		}
		HikariConfig poolConfiguration=task.getPoolConfiguration(project.extensions.exportXml.dataSource);
		assertEquals("org.hsqldb.jdbc.JDBCDriver", poolConfiguration.driverClassName)
	}

}
