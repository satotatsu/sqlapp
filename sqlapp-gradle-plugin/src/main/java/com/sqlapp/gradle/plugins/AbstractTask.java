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

package com.sqlapp.gradle.plugins;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.gradle.plugins.extension.AbstractExtension;
import com.sqlapp.gradle.plugins.properties.ConsoleOutputLevelTaskProperty;
import com.sqlapp.gradle.plugins.properties.ContextTaskProperty;
import com.sqlapp.gradle.plugins.properties.DebugTaskProperty;
import com.sqlapp.gradle.plugins.properties.TaskPropertiesEnum;
import com.sqlapp.gradle.plugins.util.JarNameUtils;

@DisableCachingByDefault
public abstract class AbstractTask<T extends AbstractCommand, S> extends DefaultTask
		implements DebugTaskProperty, ContextTaskProperty, ConsoleOutputLevelTaskProperty {

	public AbstractTask() {
		TaskPropertiesEnum.initializeAll(getProject(), this);
		this.command = createCommand();
		this.extension = createExtension(getProject());
		if (this.extension instanceof AbstractExtension) {
			final AbstractExtension ext = (AbstractExtension) extension;
			if (ext.getEnable().isPresent()) {
				this.setEnabled(ext.getEnable().get());
			}
		}
	}

	private T command;
	private S extension;

	@TaskAction
	public void exec() {
		run(command);
	}

	protected abstract T createCommand();

	protected abstract S createExtension(Project project);

	@Internal
	protected S getExtension() {
		return this.extension;
	}

	protected void run(T command) {
		if (this.getEnabled()) {
			final ClassLoader createCls = getClassLoaderInstance(this.getProject());
			final ClassLoader cls = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(createCls);
				try {
					if (extension != null) {
						TaskPropertiesEnum.setAllProperties(extension, command);
						final AbstractExtension ext = (AbstractExtension) extension;
						ext.initializeCommand(command);
					} else {
						// Extensionがない場合は自分自身のを使用する
						TaskPropertiesEnum.setDebugProperties(this, command);
						TaskPropertiesEnum.setAllProperties(this, command);
					}
					command.run();
				} catch (Exception e) {
					e.printStackTrace();
					throw e;
				}
			} finally {
				Thread.currentThread().setContextClassLoader(cls);
			}
		} else {
			System.out.println("This task is disabled.");
		}
	}

	private static final Map<String, ClassLoader> CACHE = new ConcurrentHashMap<>();

	private static ClassLoader getClassLoaderInstance(Project project) {
		return CACHE.computeIfAbsent(project.getPath(), p -> createClassLoader(project));
	}

	private static ClassLoader createClassLoader(Project project) {
		final Set<File> files = new HashSet<>();
		final Configuration runtimeClasspath = project.getConfigurations().findByName("runtimeClasspath");
		if (runtimeClasspath != null && runtimeClasspath.isCanBeResolved()) {
			files.addAll(runtimeClasspath.resolve());
		}
		if (files.isEmpty()) {
			return project.getClass().getClassLoader();
		}
		files.removeIf(f -> isDeleteTarget(f));
		final URL[] urls = files.stream().map(File::toURI).map(t -> {
			try {
				return t.toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}).toArray(URL[]::new);
		final ClassLoader cl = new URLClassLoader(urls, project.getClass().getClassLoader());
		return cl;
	}

	protected static boolean isDeleteTarget(File f) {
		return JarNameUtils.isTarget(f);
	}
}
