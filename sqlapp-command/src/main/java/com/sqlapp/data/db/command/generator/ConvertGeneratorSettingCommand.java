/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.FileFilterProperty;
import com.sqlapp.data.db.command.properties.GeneratorSettingFactoryProperty;
import com.sqlapp.data.db.command.properties.RecursiveProperty;
import com.sqlapp.data.db.command.properties.RemoveOriginalFileProperty;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class ConvertGeneratorSettingCommand extends AbstractCommand implements DirectoryProperty, FileFilterProperty,
		GeneratorSettingFactoryProperty, RecursiveProperty, RemoveOriginalFileProperty {
	/** input file directory */
	private File directory = new File("./");
	/** TableDataGeneratorSettingFactory */
	private TableGeneratorSettingFactory generatorSettingFactory = new TableGeneratorSettingFactory();
	/** recursive */
	private boolean recursive = false;
	/** deleteOriginalFile */
	private boolean removeOriginalFile = false;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;
	/** fileType */
	private GeneratorSettingFileType fileType = GeneratorSettingFileType.EXCEL2007;

	public ConvertGeneratorSettingCommand() {
	}

	@Override
	protected void doRun() {
		final List<TableGeneratorSetting> tableSettings;
		try {
			tableSettings = readSetting();
		} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
			throw new RuntimeException(e);
		}
		if (tableSettings.isEmpty()) {
			info("File not found. settingDirectory=" + directory.getAbsolutePath());
			return;
		}
		if (this.getFileType() == null) {
			info("Convert file type is null.");
			return;
		}
		for (TableGeneratorSetting setting : tableSettings) {
			if (setting.getFileType() != this.getFileType()) {
				try {
					setting.setFileType(this.getFileType());
					generatorSettingFactory.writeFile(setting.getParentDirectory(), setting);
					if (isRemoveOriginalFile()) {
						setting.getFile().delete();
					}
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
					getExceptionHandler().handle(e);
				}
			}
		}
	}

	private List<TableGeneratorSetting> readSetting()
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		if (this.getDirectory() == null) {
			return Collections.emptyList();
		}
		final File[] files = this.getDirectory().listFiles();
		if (files == null) {
			return Collections.emptyList();
		}
		final List<TableGeneratorSetting> ret = CommonUtils.list();
		for (File file : files) {
			addTableGeneratorSetting(file, ret);
		}
		return ret;
	}

	private void addTableGeneratorSetting(File file, final List<TableGeneratorSetting> list) {
		if (file.isFile()) {
			if (!this.getFileFilter().test(file)) {
				return;
			}
			final TableGeneratorSetting setting = this.getGeneratorSettingFactory().fromFile(file);
			if (setting != null) {
				list.add(setting);
			}
		} else {
			if (!this.isRecursive()) {
				return;
			}
			final File[] children = file.listFiles();
			if (children == null) {
				return;
			}
			for (File child : children) {
				addTableGeneratorSetting(child, list);
			}
		}
	}

	@Override
	public Predicate<File> getFileFilter() {
		return fileFilter;
	}

	@Override
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}
}
