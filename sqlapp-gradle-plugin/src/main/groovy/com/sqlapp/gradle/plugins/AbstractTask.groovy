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

import com.sqlapp.data.db.command.AbstractCommand;
import java.io.File;
import java.util.List;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Internal;

import com.sqlapp.util.CommonUtils;

abstract class AbstractTask extends DefaultTask {
	
	protected void run(AbstractCommand command){
		if (this.parameters!=null){
			command.context.putAll(parameters);
		}
		if (isDebug()){
			println("parameters="+this.parameters);
		}
		if (this.enable){
			try{
				command.run();
			} catch (Exception e){
				e.printStackTrace();
				throw e;
			}
		}else{
			println("This task is disabled.");
		}
	}

	@Input
	@Optional
	Boolean enable=true;
	@Input
	@Optional
	Boolean debug;
	@Input
	@Optional
	Map<String,Object> parameters;
	
	public void setEnable(Boolean enable){
		this.enable=enable==null?true:enable;
	}

	public void setDebug(Boolean debug){
		this.debug=debug==null?false:debug;
	}

	void enable(boolean enable){
		setEnable(enable);
	}

	void debug(boolean debug){
		setDebug(debug);
	}
	
	@Internal
	protected boolean isDebug(){
		return debug;
	}
	
	/**
	 * @return the file
	 */
	protected File getFile(def file) {
		if (file==null){
			return null;
		}
		return project.file(file);
	}
	
	/**
	 * @return the files
	 */
	protected List<File> getFiles(def files) {
		if (files==null){
			return Collections.emptyList();
		}
		return CommonUtils.list(project.files(files).getFiles());
	}

}
