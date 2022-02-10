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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.isEmpty;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ClassNamePrefixProperty;
import com.sqlapp.data.schemas.properties.ClassNameProperty;
import com.sqlapp.data.schemas.properties.LanguageProperty;
import com.sqlapp.data.schemas.properties.MethodNameProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Function、Procedure、Triggerの親クラス
 * 
 * @author satoh
 * 
 */
public abstract class Routine<T extends Routine<T>> extends
		AbstractSchemaObject<T> implements ClassNamePrefixProperty<T>,ClassNameProperty<T>, MethodNameProperty<T>, SpecificNameProperty<T>, LanguageProperty<T> {

	/** serialVersionUID */
	private static final long serialVersionUID = 8421121636119616875L;

	/**
	 * コンストラクタ
	 */
	protected Routine() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Routine(String name) {
		super(name);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 * @param specificName
	 */
	public Routine(String name, String specificName) {
		super(name, specificName);
	}

	/** クラス名プレフィクス */
	private String classNamePrefix = null;
	/** クラス名 */
	private String className = null;
	/** メソッド名 */
	private String methodName = null;
	/** 言語(SQL,c,javaなど) */
	private String language = null;
	/** 固有名称(ファンクション、プロシージャのオーバーロード時に区別するための名称) */
	private String specificName = null;

	@Override
	public String getSpecificName() {
		if (isEmpty(specificName)) {
			return getName();
		}
		return specificName;
	}

	@Override
	public T setSpecificName(String specificName) {
		if (specificName == null) {
			this.specificName = specificName;
			return instance();
		}
		String[] names = specificName.split("\\.");
		int i = 0;
		if (names.length > 1) {
			this.setSchemaName(names[i++]);
		}
		this.specificName = names[i++];
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractSchemaObject#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Routine)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		T val = cast(obj);
		if (!equals(SchemaProperties.CLASS_NAME_PREFIX, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CLASS_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.METHOD_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.LANGUAGE
				, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(emptyToNull(val.getLanguage()), emptyToNull(this.getLanguage())))) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObject#toStringDetail(com.sqlapp
	 * .util.ToStringBuilder)
	 */
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.CLASS_NAME_PREFIX, this.getClassNamePrefix());
		builder.add(SchemaProperties.CLASS_NAME, this.getClassName());
		builder.add(SchemaProperties.METHOD_NAME, this.getMethodName());
		builder.add(SchemaProperties.LANGUAGE, this.getLanguage());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.CLASS_NAME_PREFIX.getLabel(), this.getClassNamePrefix());
		stax.writeAttribute(SchemaProperties.CLASS_NAME.getLabel(), this.getClassName());
		stax.writeAttribute(SchemaProperties.METHOD_NAME.getLabel(), this.getMethodName());
		if (!isEmpty(getLanguage())) {
			stax.writeAttribute(SchemaProperties.LANGUAGE.getLabel(), this.getLanguage());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	/**
	 * sepecificNameの再設定に合わせて、親をresetします
	 */
	protected void renewParent() {
		if (this.getParent() != null) {
			if (this.getParent() instanceof AbstractBaseDbObjectCollection){
				((AbstractBaseDbObjectCollection<?>)this.getParent()).renew();
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ClassNameProperty#getClassNamePrefix()
	 */
	@Override
	public String getClassNamePrefix() {
		return classNamePrefix;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ClassNameProperty#setClassNamePrefix(java.lang.String)
	 */
	@Override
	public T setClassNamePrefix(String classNamePrefix) {
		this.classNamePrefix = classNamePrefix;
		return instance();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ClassNameProperty#getClassName()
	 */
	@Override
	public String getClassName() {
		return className;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ClassNameProperty#setClassName(java.lang.String)
	 */
	@Override
	public T setClassName(String className) {
		this.className = className;
		return instance();
	}

	/**
	 * @return メソッド名を取得する
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * @param methodName
	 *            メソッド名を設定する
	 */
	public T setMethodName(String methodName) {
		this.methodName = methodName;
		return instance();
	}

	/**
	 * @return the lang
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * @param language
	 *            the language to set
	 */
	public T setLanguage(String language) {
		this.language = language;
		return instance();
	}

}
