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
import java.util.Locale;
import java.util.function.Predicate;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.FileFilterProperty;
import com.sqlapp.data.db.command.properties.FileTypeProperty;
import com.sqlapp.data.db.command.properties.GeneratorConfigFactoryProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.RecursiveProperty;
import com.sqlapp.data.db.command.properties.RemoveOriginalFileProperty;
import com.sqlapp.exceptions.InvalidPropertyException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class ConvertGeneratorConfigCommand extends AbstractCommand
		implements DirectoryProperty, OutputDirectoryProperty, FileFilterProperty, GeneratorConfigFactoryProperty,
		RecursiveProperty, RemoveOriginalFileProperty, FileTypeProperty {
	/** input file directory */
	private File directory = new File("./");
	/** input file directory */
	private File outputDirectory;
	/** TableDataGeneratorConfigFactory */
	private TableGeneratorConfigFactory generatorConfigFactory = new TableGeneratorConfigFactory();
	/** recursive */
	private boolean recursive = false;
	/** deleteOriginalFile */
	private boolean removeOriginalFile = false;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;
	/** fileType */
	private String fileType = "xlsx";
	/** locale */
	private Locale locale = Locale.getDefault();

	public ConvertGeneratorConfigCommand() {
	}

	@Override
	protected void doRun() {
		final List<TableGeneratorConfig> tableConfigs;
		final ConfigFileType configFileType = ConfigFileType.parse(this.getFileType());
		if (configFileType == null) {
			throw new InvalidPropertyException("fileType", this.getFileType());
		}
		try {
			tableConfigs = readConfig();
		} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
			throw new RuntimeException(e);
		}
		if (tableConfigs.isEmpty()) {
			info("File not found. configDirectory=" + directory.getAbsolutePath());
			return;
		}
		if (this.getFileType() == null) {
			info("Convert file type is null.");
			return;
		}
		for (TableGeneratorConfig config : tableConfigs) {
			if (config.getFileType() != configFileType) {
				try {
					config.setFileType(configFileType);
					if (equalsDirectory()) {
						generatorConfigFactory.writeFile(config.getParentDirectory(), locale, config);
					} else {
						String path = extractDirectory(config.getParentDirectory());// 相対パス取得
						if (path == null) {
							generatorConfigFactory.writeFile(getOutputDirectory(), locale, config);
						} else {
							String parentDirPath = FileUtils.combinePath(outputDirectory.getAbsolutePath(), path);
							File dir = new File(parentDirPath);
							generatorConfigFactory.writeFile(dir, locale, config);
						}
					}
					if (isRemoveOriginalFile()) {
						config.getFile().delete();
					}
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
					getExceptionHandler().handle(e);
				}
			}
		}
	}

	private String extractDirectory(File configDir) {
		if (CommonUtils.eq(directory.getAbsolutePath(), configDir.getAbsolutePath())) {
			return null;
		}
		String path = configDir.getAbsolutePath().replace(directory.getAbsolutePath(), "");
		if (CommonUtils.isEmpty(path) || "/".endsWith(path) || "\\".equals(path)) {
			return null;
		}
		return path;
	}

	private boolean equalsDirectory() {
		if (this.outputDirectory == null) {
			return true;
		}
		if (CommonUtils.eq(directory.getAbsolutePath(), outputDirectory.getAbsolutePath())) {
			return true;
		}
		return false;
	}

	private List<TableGeneratorConfig> readConfig()
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		if (this.getDirectory() == null) {
			return Collections.emptyList();
		}
		final File[] files = this.getDirectory().listFiles();
		if (files == null) {
			return Collections.emptyList();
		}
		final List<TableGeneratorConfig> ret = CommonUtils.list();
		for (File file : files) {
			addTableGeneratorConfig(file, ret);
		}
		return ret;
	}

	private void addTableGeneratorConfig(File file, final List<TableGeneratorConfig> list) {
		if (file.isFile()) {
			if (!this.getFileFilter().test(file)) {
				return;
			}
			final TableGeneratorConfig config = this.getGeneratorConfigFactory().fromFile(file);
			if (config != null) {
				list.add(config);
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
				addTableGeneratorConfig(child, list);
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

	@Override
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public void setFileType(ConfigFileType fileType) {
		this.fileType = fileType.toString();
	}
}
