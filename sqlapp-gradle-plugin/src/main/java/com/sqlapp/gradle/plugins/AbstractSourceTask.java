/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;

import groovy.lang.Closure;

@DisableCachingByDefault
public abstract class AbstractSourceTask<T extends AbstractCommand> extends AbstractTask<T, Void> {
	@Inject
	public AbstractSourceTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	private ConfigurableFileCollection sourceFiles = this.getProject().getObjects().fileCollection();

	private final PatternFilterable patternSet = this.getPatternSetFactory().createPatternSet();

	@Inject
	protected abstract PatternSetFactory getPatternSetFactory();

	@Internal
	protected PatternFilterable getPatternSet() {
		return this.patternSet;
	}

	@InputFiles
	@Optional
	@SkipWhenEmpty
	@IgnoreEmptyDirectories
	@PathSensitive(PathSensitivity.RELATIVE)
	@ToBeReplacedByLazyProperty
	public FileTree getSource() {
		return this.sourceFiles.getAsFileTree().matching(this.patternSet);
	}

	public void setSource(FileTree source) {
		this.setSource((Object) source);

	}

	public void setSource(Object source) {
		this.sourceFiles = this.getProject().getObjects().fileCollection().from(new Object[] { source });
	}

	public AbstractSourceTask<T> source(Object... sources) {
		this.sourceFiles.from(sources);
		return this;
	}

	public AbstractSourceTask<T> include(String... includes) {
		this.patternSet.include(includes);
		return this;
	}

	public AbstractSourceTask<T> include(Iterable<String> includes) {
		this.patternSet.include(includes);
		return this;
	}

	public AbstractSourceTask<T> include(Spec<FileTreeElement> includeSpec) {
		this.patternSet.include(includeSpec);
		return this;
	}

	public AbstractSourceTask<T> include(Closure<?> includeSpec) {
		this.patternSet.include(includeSpec);
		return this;
	}

	public AbstractSourceTask<T> exclude(String... excludes) {
		this.patternSet.exclude(excludes);
		return this;
	}

	public AbstractSourceTask<T> exclude(Iterable<String> excludes) {
		this.patternSet.exclude(excludes);
		return this;
	}

	public AbstractSourceTask<T> exclude(Spec<FileTreeElement> excludeSpec) {
		this.patternSet.exclude(excludeSpec);
		return this;
	}

	public AbstractSourceTask<T> exclude(Closure<?> excludeSpec) {
		this.patternSet.exclude(excludeSpec);
		return this;
	}

	@Internal
	@ToBeReplacedByLazyProperty
	public Set<String> getIncludes() {
		return this.patternSet.getIncludes();
	}

	public AbstractSourceTask<T> setIncludes(Iterable<String> includes) {
		this.patternSet.setIncludes(includes);
		return this;
	}

	@Internal
	@ToBeReplacedByLazyProperty
	public Set<String> getExcludes() {
		return this.patternSet.getExcludes();
	}

	public AbstractSourceTask<T> setExcludes(Iterable<String> excludes) {
		this.patternSet.setExcludes(excludes);
		return this;
	}

}