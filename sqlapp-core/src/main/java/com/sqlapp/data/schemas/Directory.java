/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DirectoryPathProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ディレクトリに対応したオブジェクト(Oracle専用)
 * 
 * @author satoh
 * 
 */
public final class Directory extends AbstractNamedObject<Directory> implements
		HasParent<DirectoryCollection>, DirectoryPathProperty<Directory> {
	/** serialVersionUID */
	private static final long serialVersionUID = 2944673540434794114L;
	/** ディレクトリパス */
	private String directoryPath = null;

	public Directory() {
	}

	public Directory(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Directory> newInstance(){
		return ()->new Directory();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Directory)) {
			return false;
		}
		Directory val = (Directory) obj;
		if (!equals(SchemaProperties.DIRECTORY_PATH, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DIRECTORY_PATH, getDirectoryPath());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DIRECTORY_PATH.getLabel(), this.getDirectoryPath());
	}

	/**
	 * @return the directoryPath
	 */
	@Override
	public String getDirectoryPath() {
		return directoryPath;
	}

	/**
	 * @param directoryPath
	 *            the directoryPath to set
	 */
	@Override
	public Directory setDirectoryPath(String directoryPath) {
		this.directoryPath = directoryPath;
		return instance();
	}

	@Override
	public DirectoryCollection getParent() {
		return (DirectoryCollection) super.getParent();
	}

}
