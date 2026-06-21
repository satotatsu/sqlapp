/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.exceptions.CommandException;

public class DuplicateVersionFileException extends CommandException {

	/** serialVersionUID */
	private static final long serialVersionUID = 5001165500648206966L;
	private final boolean versionUp;

	private final long versionNumber;

	private final File file;

	private final File duplicateFile;

	/**
	 * @return the versionUp
	 */
	public boolean isVersionUp() {
		return versionUp;
	}

	/**
	 * @return the versionNumber
	 */
	public long getVersionNumber() {
		return versionNumber;
	}

	/**
	 * @return the file
	 */
	public File getFile() {
		return file;
	}

	/**
	 * @return the duplicateFile
	 */
	public File getDuplicateFile() {
		return duplicateFile;
	}

	public DuplicateVersionFileException(boolean versionUp, long versionNumber, File file, File duplicateFile) {
		super(createMessage(versionUp, versionNumber, file, duplicateFile));
		this.versionUp = versionUp;
		this.versionNumber = versionNumber;
		this.file = file;
		this.duplicateFile = duplicateFile;
	}

	private static String createMessage(boolean versionUp, long versionNumber, File file, File duplicateFile) {
		StringBuilder builder = new StringBuilder();
		builder.append("version");
		if (versionUp) {
			builder.append(" up");
		} else {
			builder.append(" down");
		}
		builder.append(" file is duplicated.");
		builder.append(" versionNumber=[");
		builder.append(versionNumber);
		builder.append("]");
		builder.append(", file=[");
		builder.append(file.getAbsolutePath());
		builder.append("]");
		builder.append(", duplicateFile=[");
		builder.append(duplicateFile.getAbsolutePath());
		builder.append("]");
		return builder.toString();
	}
}
