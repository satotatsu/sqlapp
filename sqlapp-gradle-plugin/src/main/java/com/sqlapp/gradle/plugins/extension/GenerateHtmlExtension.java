package com.sqlapp.gradle.plugins.extension;

import java.io.File;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.GenerateHtmlCommand;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

public abstract class GenerateHtmlExtension extends AbstractSchemaFileExtension {
	@Inject
	public GenerateHtmlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<GenerateHtmlExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<RenderOptionExtension> getRenderOptions();

	/**
	 * file
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	@Optional
	public abstract Property<String> getDiagramFont();

	@Input
	@Optional
	public abstract Property<String> getDiagramFormat();

	@Input
	@Optional
	public abstract Property<String> getDot();

	@Input
	@Optional
	public abstract Property<Boolean> getMultiThread();

	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	@InputDirectory
	@Optional
	public abstract DirectoryProperty getDirectory();

	@Input
	@Optional
	public abstract Property<Boolean> getUseSchemaNameDirectory();

	@Input
	@Optional
	public abstract Property<Boolean> getUseTableNameDirectory();

	/** file filter */
	@Input
	@Optional
	public abstract Property<Predicate<File>> getFileFilter();

	/** Virtual foreign Key definitions */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getForeignKeyDefinitionDirectory();

	/** virtualForeignKeyLabel */
	@Input
	@Optional
	public abstract Property<Function<ForeignKeyConstraint, String>> getVirtualForeignKeyLabel();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@Internal
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof GenerateHtmlCommand) {
			GenerateHtmlCommand com = (GenerateHtmlCommand) command;
			if (getRenderOptions().isPresent()) {
				getRenderOptions().get().setRenderOption(com.getRenderOptions());
			}
			if (getOutputDirectory().isPresent()) {
				com.setOutputDirectory(getOutputDirectory().get().getAsFile());
			}
			if (getDiagramFont().isPresent()) {
				com.setDiagramFont(getDiagramFont().get());
			}
			if (getDiagramFormat().isPresent()) {
				com.setDiagramFormat(getDiagramFormat().get());
			}
			if (getDot().isPresent()) {
				com.setDot(getDot().get());
			}
			if (getMultiThread().isPresent()) {
				com.setMultiThread(getMultiThread().get());
			}
			if (getFileDirectory().isPresent()) {
				com.setFileDirectory(getFileDirectory().get().getAsFile());
			}
			if (getDirectory().isPresent()) {
				com.setDirectory(getDirectory().get().getAsFile());
			}
			if (getUseSchemaNameDirectory().isPresent()) {
				com.setUseSchemaNameDirectory(getUseSchemaNameDirectory().get());
			}
			if (getUseTableNameDirectory().isPresent()) {
				com.setUseTableNameDirectory(getUseTableNameDirectory().get());
			}
			if (getForeignKeyDefinitionDirectory().isPresent()) {
				com.setForeignKeyDefinitionDirectory(getForeignKeyDefinitionDirectory().get().getAsFile());
			}
			if (getVirtualForeignKeyLabel().isPresent()) {
				com.setVirtualForeignKeyLabel(getVirtualForeignKeyLabel().get());
			}
			//
			if (getPlaceholderPrefix().isPresent()) {
				com.setPlaceholderPrefix(getPlaceholderPrefix().get());
			}
			if (getPlaceholderSuffix().isPresent()) {
				com.setPlaceholderSuffix(getPlaceholderSuffix().get());
			}
			if (getPlaceholders().isPresent()) {
				com.setPlaceholders(getPlaceholders().get());
			}
		}
	}
}
