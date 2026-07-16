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
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
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

	@Classpath
	public abstract ConfigurableFileCollection getRuntimeClasspath();

	@Inject
	public AbstractTask(ObjectFactory objectFactory) {
		TaskPropertiesEnum.initializeAll(getProject().getObjects(), this);
		this.command = createCommand();
		this.extension = createExtension(getProject());
		if (this.extension instanceof AbstractExtension) {
			final AbstractExtension ext = (AbstractExtension) extension;
			if (ext.getEnable().isPresent()) {
				this.setEnabled(ext.getEnable().get());
			}
		}
		//
		// Configuration Phase で取得
		final Configuration conf = getProject().getConfigurations().findByName("runtimeClasspath");
		if (conf != null && conf.isCanBeResolved()) {
			getRuntimeClasspath().from(conf);
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
			final ClassLoader createCls = getClassLoaderInstance(getRuntimeClasspath().getFiles());
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
					beforeRun(command);
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

	protected void beforeRun(T command) {

	}

	private static final Map<String, ClassLoader> CACHE = new ConcurrentHashMap<>();

	protected static ClassLoader getClassLoaderInstance(Set<File> resolvedFiles) {
		String key = resolvedFiles.stream().map(File::getAbsolutePath).sorted().collect(Collectors.joining(";"));
		return CACHE.computeIfAbsent(key, p -> createClassLoader(resolvedFiles));
	}

	protected static ClassLoader createClassLoader(Set<File> resolvedFiles) {
		final Set<File> files = new HashSet<>(resolvedFiles);
		files.removeIf(f -> isDeleteTarget(f));
		final URL[] urls = files.stream().map(File::toURI).map(t -> {
			try {
				return t.toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}).toArray(URL[]::new);
		final ClassLoader parent = AbstractTask.class.getClassLoader();
		final ClassLoader cl = new URLClassLoader(urls, parent);
		return cl;
	}

	protected static boolean isDeleteTarget(File f) {
		return JarNameUtils.isTarget(f);
	}
}
