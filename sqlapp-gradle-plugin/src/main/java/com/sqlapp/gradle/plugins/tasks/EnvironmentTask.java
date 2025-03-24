/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins.tasks;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.util.CommonUtils;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

public abstract class EnvironmentTask extends AbstractTask {

	@Optional
	@InputDirectory
	public abstract DirectoryProperty getEnvPath();

	@SuppressWarnings("unchecked")
	@TaskAction
	public void exec() {
		String env = System.getProperty("env");
		if (env == null) {
			if (getProject().hasProperty("env")) {
				env = (String) getProject().getProperties().get("env");
			}
		}
		File envPath = getEnvPath().get().getAsFile();
		if (getEnvPath().isPresent()) {
			envPath = getEnvPath().get().getAsFile();
		} else {
			envPath = new File("src/main/environment");
		}
		if (!envPath.exists()) {
			System.err.println("envPath does not exists. [" + envPath.getAbsolutePath() + "]");
			throw new InvalidUserDataException("envPath does not exists. [" + envPath.getAbsolutePath() + "]");
		}
		if (env == null) {
			Map<String, File> childMap = new HashMap<String, File>();
			for (File child : envPath.listFiles()) {
				if (child.isDirectory()) {
					childMap.put(child.getName(), child);
				}
			}
			if (childMap.isEmpty()) {
				System.err.println("No environment found. path=" + envPath.getAbsolutePath());
				throw new InvalidUserDataException("No environment found. path=" + envPath.getAbsolutePath());
			} else if (childMap.size() == 1) {
				env = CommonUtils.first(childMap.keySet());
			} else {
				String envText = getEnvText(childMap.keySet());
				Console console = System.console();
				if (console != null) {
					while (true) {
						env = console.readLine("%s:", "select environment.[" + envText + "]");
						if (childMap.containsKey(env)) {
							break;
						}
					}
				} else {
					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					while (true) {
						System.out.println("select environment.[" + envText + "]:");
						try {
							env = br.readLine();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						if (childMap.containsKey(env)) {
							break;
						}
					}
				}
			}
		}
		File envDir = new File(envPath, env);
		if (!envDir.exists()) {
			System.out.println("Env direcotry does not exists. path=" + envDir.getAbsolutePath());
			return;
		}
		if (!envDir.isDirectory()) {
			System.out.println("Env direcotry is not a directory. path=" + envDir.getAbsolutePath());
			return;
		}
		System.out.println("Environment dir [" + envDir.getAbsolutePath() + "] was selected.");
		ConfigSlurper slurper = new ConfigSlurper();
		slurper.setBinding(this.getProject().getProperties());
		ConfigObject config = new ConfigObject();
		ConfigUtils.readConfig(this.getProject().getProperties(), config, envDir.listFiles());
		System.out.println("project.getName()=" + getProject().getName());
		if (this.getProject().getParent() != null) {
			System.out.println("project.getParent().getName()=" + getProject().getParent().getName());
		}
		config.forEach((k, v) -> {
			String key = (String) k;
			Object value = v;
			System.out.println("key=" + k + ", value=" + v);
			Object obj = this.getProject().getExtensions().findByName(key);
			if (obj == null) {
				this.getProject().getExtensions().add(key, value);
			}
		});
		System.out.println("project.properties=" + this.getProject().getProperties());
	}

	private String getEnvText(Set<String> set) {
		StringBuilder builder = new StringBuilder();
		for (String value : set) {
			builder.append(value);
			builder.append(",");
		}
		return builder.substring(0, builder.length() - 1);
	}

}