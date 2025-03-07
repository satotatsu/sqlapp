/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Locale;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.PermissionSetProperty;
import com.sqlapp.data.schemas.properties.object.AssemblyFilesProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * CLRアセンブリ
 * 
 * @author satoh
 * 
 */
public class Assembly extends AbstractNamedObject<Assembly> implements
		HasParent<AssemblyCollection>, PermissionSetProperty<Assembly>,AssemblyFilesProperty<Assembly> {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	/** アセンブリファイルコレクション */
	private AssemblyFileCollection assemblyFiles = new AssemblyFileCollection(
			this);
	/** アセンブリに対する権限セットまたはセキュリティ レベル */
	private PermissionSet permissionSet = null;

	/**
	 * コンストラクタ
	 */
	public Assembly() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Assembly(String name) {
		super(name);
	}

	@Override
	protected Supplier<Assembly> newInstance(){
		return ()->new Assembly();
	}
	
	@Override
	public String getSpecificName() {
		return this.getName();
	}

	/**
	 * 新規にAssemblyFileを作成します
	 * 
	 */
	public AssemblyFile newAssemblyFile() {
		AssemblyFile file = new AssemblyFile();
		file.setParent(assemblyFiles);
		return file;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Assembly)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Assembly val = cast(obj);
		if (!equals(SchemaObjectProperties.ASSEMBLY_FILES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PERMISSION_SET, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.PERMISSION_SET.getLabel(), this.getPermissionSet());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(assemblyFiles)) {
			assemblyFiles.writeXml(stax);
		}
	}

	protected void setAssemblyFiles(AssemblyFileCollection assemblyFiles) {
		this.assemblyFiles = assemblyFiles;
		if (this.assemblyFiles != null) {
			this.assemblyFiles.setParent(this);
		}
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.ASSEMBLY_FILES, this.getAssemblyFiles());
		builder.add(SchemaProperties.PERMISSION_SET, this.getPermissionSet());
	}

	@Override
	public AssemblyFileCollection getAssemblyFiles() {
		return assemblyFiles;
	}

	/**
	 * @return the permissionSet
	 */
	@Override
	public PermissionSet getPermissionSet() {
		return permissionSet;
	}

	/**
	 * @param permissionSet
	 *            the permissionSet to set
	 */
	@Override
	public Assembly setPermissionSet(PermissionSet permissionSet) {
		this.permissionSet = permissionSet;
		return this;
	}

	@Override
	public AssemblyCollection getParent() {
		return (AssemblyCollection) super.getParent();
	}

	/**
	 * アセンブリに対する権限セットまたはセキュリティ レベル
	 * 
	 * @author tatsuo satoh
	 * 
	 */
	public enum PermissionSet implements EnumProperties {
		Safe, External, Unsafe;

		@Override
		public String getDisplayName() {
			return getDisplayName(Locale.ENGLISH);
		}

		@Override
		public String getDisplayName(Locale locale) {
			return this.toString();
		}

		@Override
		public String getSqlValue() {
			return this.toString().toUpperCase();
		}

		public static PermissionSet parse(String value) {
			if (value == null) {
				return null;
			}
			if (value.toUpperCase().startsWith("S")) {
				return Safe;
			}
			if (value.toUpperCase().startsWith("U")) {
				return Unsafe;
			}
			if (value.toUpperCase().startsWith("E")) {
				return External;
			}
			return null;
		}

	}
}
