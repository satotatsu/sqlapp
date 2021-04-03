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
package com.sqlapp.util.file;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.PaddingType;

import lombok.Data;
/**
 * バイト長固定ファイルの定義クラス
 * @author satot
 *
 */
public class FixedByteLengthFileSetting implements Serializable,Cloneable {

	/** serialVersionUID */
	private static final long serialVersionUID = -8457129568589208110L;
	private static final FixedByteLengthFieldSetting[] ENPTY_FIELDS=new FixedByteLengthFieldSetting[0]; 
	private PaddingType paddingType;
	private String padding=" ";
	private String lineBreak = "\n";
	private byte[] paddingBytes;
	private byte[] lineBreakBytes;
	private final String separator = "";
	private byte[] separatorBytes;
	private Charset charset;
	private int bufferSize=0;
	private Map<String,FixedByteLengthFieldSetting> fixedByteLengthFieldMap = CommonUtils.caseInsensitiveLinkedMap();
	private FixedByteLengthFieldSetting[] fixedByteLengthFields = ENPTY_FIELDS;
	private Table table;
	
	@Override
	public FixedByteLengthFileSetting clone() {
		FixedByteLengthFileSetting clone;
		try {
			clone=(FixedByteLengthFileSetting)super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		clone.fixedByteLengthFieldMap = CommonUtils.caseInsensitiveLinkedMap();
		clone.fixedByteLengthFields=new FixedByteLengthFieldSetting[this.fixedByteLengthFields.length];
		for(int i=0;i<clone.fixedByteLengthFields.length;i++) {
			clone.fixedByteLengthFields[i]=this.fixedByteLengthFields[i].clone();
			clone.fixedByteLengthFieldMap.put(clone.fixedByteLengthFields[i].getName(), clone.fixedByteLengthFields[i]);
		}
		return clone;
	}
	
	public void addField(final String fieldName, final Consumer<FixedByteLengthFieldSetting> cons) {
		final FixedByteLengthFieldSetting field=new FixedByteLengthFieldSetting();
		field.setName(fieldName);
		cons.accept(field);
		fixedByteLengthFieldMap.put(fieldName, field);
		final List<FixedByteLengthFieldSetting> list=fixedByteLengthFieldMap.entrySet().stream().map(e->e.getValue()).collect(Collectors.toList());
		fixedByteLengthFields = list.toArray(new FixedByteLengthFieldSetting[0]);
	}

	public void addField(final Column column, final Consumer<FixedByteLengthFieldSetting> cons) {
		addField(column.getName(), field->{
			if (table==null) {
				table=column.getTable();
			}
			if (table!=null&&table!=column.getTable()) {
				throw new IllegalArgumentException("Multiple table does not support.");
			}
			field.setColumn(column);
			if (column.getLength()!=null) {
				field.setLength(column.getLength().intValue());
			}
			if (column.getDataType()!=null&&column.getDataType().isCharacter()) {
				field.setPaddingType(PaddingType.RIGHT);
				field.setPadding(" ");
			}
			if (column.getDataType()!=null&&column.getDataType().isNumeric()) {
				field.setPaddingType(PaddingType.LEFT);
				field.setPadding(" ");
			}
			field.setConverter(column.getConverter());
			cons.accept(field);
		});
	}

	public void addField(final ColumnCollection columns, final Consumer<FixedByteLengthFieldSetting> cons) {
		for(final Column column:columns) {
			addField(column, cons);
		}
	}

	protected void initialize(final Charset charset) {
		int len=0;
		this.charset=charset;
		this.paddingBytes=this.padding.getBytes(charset);
		this.separatorBytes=this.separator.getBytes(charset);
		this.lineBreakBytes=this.lineBreak.getBytes(charset);
		boolean first=true;
		for(final FixedByteLengthFieldSetting fixedByteField:fixedByteLengthFields) {
			if (!first) {
				len=len+this.separatorBytes.length;
			}
			if (fixedByteField.padding!=null) {
				fixedByteField.paddingBytes=fixedByteField.padding.getBytes(charset);
			} else {
				fixedByteField.paddingBytes=this.paddingBytes;
			}
			fixedByteField.buffer=new byte[fixedByteField.length];
			len=len+fixedByteField.length;
			first=false;
		}
		if (!CommonUtils.isEmpty(lineBreak)) {
			len=len+this.lineBreakBytes.length;
		}
		bufferSize=len;
	}

	@Data
	public static class FixedByteLengthFieldSetting implements Serializable, Cloneable {
		/** serialVersionUID */
		private static final long serialVersionUID = -4720756606761967798L;
		private String name;
		private int length;
		private byte[] buffer;
		private PaddingType paddingType;
		private String padding;
		private byte[] paddingBytes;
		private Converter<?> converter;
		private Column column;

		@Override
		public FixedByteLengthFieldSetting clone() {
			try {
				return (FixedByteLengthFieldSetting)super.clone();
			} catch (final CloneNotSupportedException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public byte[] createBuffer() {
		return new byte[this.bufferSize];
	}
	
	protected Row toRow(final byte[] buffer) {
		int position=0;
		boolean first=true;
		final Row row=this.table.newRow();
		for(int i=0;i<fixedByteLengthFields.length;i++) {
			if (!first) {
				position=position+this.separatorBytes.length;
			}
			final FixedByteLengthFieldSetting fieldSetting=fixedByteLengthFields[i];
			System.arraycopy(buffer, position, fieldSetting.buffer, 0, fieldSetting.getLength());
			final byte[] bytes=fieldSetting.paddingType.trimPadding(fieldSetting.buffer, fieldSetting.paddingBytes);
			final String text;
			if (bytes.length!=0) {
				text=new String(bytes, this.charset);
			}else {
				text="";
			}
			final Object obj=fieldSetting.getConverter().convertObject(text);
			row.put(fieldSetting.column, obj);
			position=position+fieldSetting.length;
			first=false;
		}
		return table.newRow();
	}
	
	public String getLineBreak() {
		return lineBreak;
	}

	public void setLineBreak(final String lineBreak) {
		this.lineBreak = lineBreak;
	}

	public PaddingType getPaddingType() {
		return paddingType;
	}

	public void setPaddingType(final PaddingType paddingType) {
		this.paddingType = paddingType;
	}

	public String getPadding() {
		return padding;
	}

	public void setPadding(final String padding) {
		this.padding = padding;
	}

	protected Map<String, FixedByteLengthFieldSetting> getFixedByteLengthFieldMap() {
		return fixedByteLengthFieldMap;
	}

	protected FixedByteLengthFieldSetting[] getFixedByteLengthFields() {
		return fixedByteLengthFields;
	}

}
