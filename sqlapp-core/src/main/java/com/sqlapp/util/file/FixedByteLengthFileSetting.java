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
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.PaddingType;

import lombok.Data;
/**
 * バイト長固定ファイルの定義クラス
 * @author satot
 *
 */
public class FixedByteLengthFileSetting {

	private PaddingType paddingType;
	private String padding;
	private String lineBreak = "\n";
	private final String separator = "";

	private final Map<String,FixedByteLengthFieldSetting> fixedByteLengthFieldMap = CommonUtils.caseInsensitiveLinkedMap();
	private static final FixedByteLengthFieldSetting[] ENPTY_FIELDS=new FixedByteLengthFieldSetting[0]; 
	private FixedByteLengthFieldSetting[] fixedByteLengthFields = ENPTY_FIELDS;

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

	protected int caluculateBufferSize(final Charset charset) {
		int len=0;
		final int separatorSize=this.separator.getBytes(charset).length;
		boolean first=true;
		for(final FixedByteLengthFieldSetting fixedByteField:fixedByteLengthFields) {
			if (!first) {
				len=len+separatorSize;
			}
			len=len+fixedByteField.length;
			first=false;
		}
		if (!CommonUtils.isEmpty(lineBreak)) {
			len=len+lineBreak.getBytes(charset).length;
		}
		return len;
	}

	@Data
	public static class FixedByteLengthFieldSetting implements Serializable {
		/** serialVersionUID */
		private static final long serialVersionUID = -4720756606761967798L;
		private String name;
		private int length;
		private PaddingType paddingType;
		private String padding;
		private Converter<?> converter;
		private Column column;
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
