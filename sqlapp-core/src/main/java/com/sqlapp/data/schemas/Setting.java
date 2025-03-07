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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DefaultProperty;
import com.sqlapp.data.schemas.properties.DisplayValueProperty;
import com.sqlapp.data.schemas.properties.ValueProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Settingに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class Setting extends AbstractNamedObject<Setting> implements
		HasParent<SettingCollection>
	,ValueProperty<Setting>
	,DisplayValueProperty<Setting>
	,DefaultProperty<Setting>
	{
	/** serialVersionUID */
	private static final long serialVersionUID = 2944673540434794114L;
	/** 値 */
	private String value = null;
	/** 表示値 */
	private String displayValue = null;
	/** デフォルト */
	private boolean _default = (Boolean)SchemaProperties.DEFAULT.getDefaultValue();

	public Setting() {
	}

	public Setting(String name) {
		super(name);
	}

	@Override
	protected Supplier<Setting> newInstance(){
		return ()->new Setting();
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
		if (!(obj instanceof Setting)) {
			return false;
		}
		Setting val = (Setting) obj;
		if (!equals(SchemaProperties.VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DISPLAY_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.VALUE, getValue());
		builder.add(SchemaProperties.DISPLAY_VALUE, getDisplayValue());
		builder.add(SchemaProperties.DEFAULT, isDefault());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.VALUE.getLabel(), this.getValue());
		stax.writeAttribute(SchemaProperties.DISPLAY_VALUE.getLabel(), this.getDisplayValue());
		stax.writeAttribute(SchemaProperties.DEFAULT.getLabel(), this.isDefault());
	}

	/**
	 * @return the value
	 */
	@Override
	public String getValue() {
		return value;
	}

	/**
	 * @param value
	 *            the value to set
	 */
	@Override
	public Setting setValue(String value) {
		this.value = value;
		return this;
	}

	/**
	 * @return the displayValue
	 */
	@Override
	public String getDisplayValue() {
		return displayValue;
	}

	/**
	 * @param displayValue
	 *            the displayValue to set
	 */
	@Override
	public Setting setDisplayValue(String displayValue) {
		this.displayValue = displayValue;
		return this;
	}
	
	@Override
	public boolean isDefault() {
		return _default;
	}

	@Override
	public Setting setDefault(boolean _default) {
		this._default = _default;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public SettingCollection getParent() {
		return (SettingCollection) super.getParent();
	}

}
