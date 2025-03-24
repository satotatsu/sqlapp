package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;

/**
 * ExportData用のExtension
 */
public abstract class ExportDataExtension extends AbstractExportDataExtension {
	@Inject
	public ExportDataExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<ExportDataExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Export対象が指定されなかった場合のExportをデフォルトとする
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDefaultExport();

	/**
	 * Output File Type
	 */
	@Input
	@Optional
	public abstract Property<String> getOutputFileType();

	@Input
	@Optional
	public abstract Property<String> getSheetName();

	@Input
	@Optional
	public abstract Property<Converters> getConverters();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof ExportData2FileCommand) {
			ExportData2FileCommand com = (ExportData2FileCommand) command;
			if (getDefaultExport().isPresent()) {
				com.setDefaultExport(getDefaultExport().get());
			}
			if (getOutputFileType().isPresent()) {
				com.setOutputFileType(WorkbookFileType.parse(getOutputFileType().get()));
			}
			if (getSheetName().isPresent()) {
				com.setSheetName(getSheetName().get());
			}
			if (getConverters().isPresent()) {
				com.setConverters(getConverters().get());
			}
		}
	}
}
