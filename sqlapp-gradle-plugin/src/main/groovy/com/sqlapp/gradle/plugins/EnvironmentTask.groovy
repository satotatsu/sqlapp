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

import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class EnvironmentTask extends DefaultTask {
	
	@Optional
	@InputDirectory
	def envPath=new File("./src/main/environment");

	@TaskAction
	def exec() {
		String env=System.getProperty("env");
		if (env==null){
			if(project.hasProperty("env")){
				env=project.getProperties().get("env");
			}
		}
		File envPath=this.getFile(envPath);
		if (!envPath.exists()){
			System.err.println("envPath does not exists. ["+envPath.absolutePath+"]");
			throw new InvalidUserDataException("envPath does not exists. ["+envPath.absolutePath+"]");
		}
		if (env==null){
			Map<String,File> childMap=new HashMap<String,File>();
			for(File child:envPath.listFiles()){
				if (child.isDirectory()){
					childMap.put(child.name, child);
				}
			}
			if (childMap.isEmpty()){
				System.err.println("No environment found. path="+envPath.absolutePath);
				throw new InvalidUserDataException("No environment found. path="+envPath.absolutePath);
			} else if (childMap.size()==1){
				env=CommonUtils.first(childMap.keySet())
			} else{
				String envText=getEnvText(childMap.keySet());
				Console console=System.console();
				if (console!=null){
					while(true){
						env=console.readLine("%s:", "select environment.["+envText+"]");
						if (childMap.containsKey(env)){
							break;
						}
					}
				} else{
					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					while(true){
						System.out.println("select environment.["+envText+"]:");
						env=br.readLine();
						if (childMap.containsKey(env)){
							break;
						}
					}
				}
			}
		}
		File envDir=new File(envPath, env);
		if (!envDir.exists()){
			System.out.println("Env direcotry does not exists. path="+envDir.absolutePath);
			return;
		}
		if (!envDir.isDirectory()){
			System.out.println("Env direcotry is not a directory. path="+envDir.absolutePath);
			return;
		}
		System.out.println("Environment dir ["+envDir.absolutePath+"] was selected.");
		ConfigSlurper slurper = new ConfigSlurper()
		slurper.binding = project.properties
		def config = project.files(envDir.listFiles()).inject(new ConfigObject()) { config, file ->
			if (file.exists()&&!file.isDirectory()){
				if (file.getAbsolutePath().endsWith(".properties")){
					Properties prop = new Properties()
					prop.load(new FileInputStream(file))
					return config.merge(slurper.parse(prop));
				} else{
					return config.merge(slurper.parse(file.toURL()));
				}
			} else{
				return config;
			}
		}
		System.out.println("project.getName()="+project.getName());
		if (project.getParent()!=null){
			System.out.println("project.getParent().getName()="+project.getParent().getName());
		}
		config.each { k, v ->
//			project.ext.k=v;
			System.out.println("k="+k);
			System.out.println("v="+v);
			project.extensions.add(k, v);
			project.properties.put(k,v);
		}
	}

	private String getEnvText(Set<String> set){
		StringBuilder builder=new StringBuilder();
		for(String value:set){
			builder.append(value);
			builder.append(",");
		}
		return builder.substring(0, builder.length()-1);
	}
	
	/**
	 * @return the file
	 */
	protected File getFile(def file) {
		return project.file(file);
	}

}
