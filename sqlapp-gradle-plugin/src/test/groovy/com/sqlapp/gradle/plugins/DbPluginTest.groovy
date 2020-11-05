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

import com.sqlapp.data.db.command.export.ExportData2FileCommand
import com.sqlapp.data.db.sql.Options
import org.gradle.api.Plugin
import org.gradle.api.Project;
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction;
import org.gradle.testfixtures.ProjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sqlapp.gradle.plugins.pojo.*;
import org.junit.jupiter.api.Test;

class DbPluginTest {
	@Test
    public void applyTest() {
        Project parentProject = ProjectBuilder.builder().withProjectDir(new File("./master")).build();
        Project project = ProjectBuilder.builder().withProjectDir(new File("./")).withParent(parentProject).build();
		project.extensions.loadTimeEnvironment=true;
		project.extensions.environmentFilePath="src/test/environment";
		//project.apply plugin: 'com.sqlapp.db'
//		project.extensions.exportXml.dataSource {
//			driverClassName=project.jdbc.connection.driverClassName
//			url=project.jdbc.connection.url
//			username=project.jdbc.connection.username
//			password=project.jdbc.connection.password
//		}
//		project.evaluate()
//		Task task=project.tasks.exportXml
//		assertEquals("root", task.pojo.dataSource.username)
//		try{
//			task.execute()
//		} catch (Exception e){
//		}
		DbPlugin plugin=new DbPlugin();
		plugin.apply(project);
		//task.exec()
	}
	
}
