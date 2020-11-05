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

import static com.sqlapp.util.BinaryUtils.toHexString;
import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.UnsupportedEncodingException;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ContentProperty;
import com.sqlapp.util.BinaryUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * CLRアセンブリファイル
 * 
 * @author satoh
 * 
 */
public final class AssemblyFile extends AbstractNamedObject<AssemblyFile>
		implements HasParent<AssemblyFileCollection>,
		ContentProperty<AssemblyFile>{
	/** serialVersionUID */
	private static final long serialVersionUID = 5364113040918889046L;
	/** ファイルのバイナリ */
	private byte[] content = null;

	public AssemblyFile() {
	}

	public AssemblyFile(String filePath) {
		super(filePath);
	}
	
	@Override
	protected Supplier<AssemblyFile> newInstance(){
		return ()->new AssemblyFile();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AssemblyFile)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		AssemblyFile val = cast(obj);
		if (!equals(SchemaProperties.CONTENT, val, equalsHandler, EqualsUtils.getEqualsSupplier(this.getContent(), val.getContent()))) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.CONTENT, toHexString(this.getContent()));
	}

	@Override
	public byte[] getContent() {
		return content;
	}

	@Override
	public AssemblyFile setContent(byte... content) {
		this.content = content;
		return this;
	}

	@Override
	public AssemblyFile setContent(String value){
		return setContent(toBinary(value));
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		try {
			if (isSource(this.getName())) {
				stax.writeCData(SchemaProperties.CONTENT.getLabel(), new String(this.getContent(), ENCODE));
			} else {
				stax.writeCData(SchemaProperties.CONTENT.getLabel(),
						BinaryUtils.encodeBase64(this.getContent()));
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public String getContentAsSource() throws UnsupportedEncodingException{
		if (isSource(this.getName())) {
			return new String(this.getContent(), ENCODE);
		}
		return null;
	}
	
	/**
	 * ソースファイルかを判定します
	 * 
	 */
	public boolean isSourceFile() {
		return isSource(this.getName());
	}

	/**
	 * ファイルパスからソースファイルかを判定します
	 * 
	 * @param filePath
	 */
	protected static boolean isSource(String filePath) {
		if (filePath == null) {
			return false;
		}
		String extension = FileUtils.getExtension(filePath);
		if (isEmpty(extension)) {
			return false;
		}
		if ("pdb".equalsIgnoreCase(extension)) {
			return false;
		}
		if ("dll".equalsIgnoreCase(extension)) {
			return false;
		}
		return true;
	}

	private static final String ENCODE = "UTF-8";

	/**
	 * アセンブリファイルをソースに変換します。
	 * 
	 * @param assemblyFile
	 */
	protected static String toSource(AssemblyFile assemblyFile) {
		if (assemblyFile == null) {
			return null;
		}
		if (isEmpty(assemblyFile.getContent())) {
			return null;
		}
		if (assemblyFile.isSourceFile()) {
			try {
				return new String(assemblyFile.getContent(), ENCODE);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		} else {
			return null;
		}
	}

	/**
	 * アセンブリのソースを文字列に変換します。
	 * 
	 * @param target
	 * @param val
	 */
	private byte[] toBinary(String val) {
		if (val == null) {
			return new byte[0];
		}
		if (isSourceFile()) {
			try {
				return val.getBytes(ENCODE);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		} else {
			return BinaryUtils.decodeBase64(val);
		}
	}

	@Override
	public AssemblyFileCollection getParent() {
		return (AssemblyFileCollection) super.getParent();
	}

}
