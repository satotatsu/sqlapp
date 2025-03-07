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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.trim;

import java.io.Serializable;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CaseSensitiveProperty;
import com.sqlapp.data.schemas.properties.DefinitionProperty;
import com.sqlapp.data.schemas.properties.DisplayNameProperty;
import com.sqlapp.data.schemas.properties.DisplayRemarksProperty;
import com.sqlapp.data.schemas.properties.ErrorMessagesProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.RemarksProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.data.schemas.properties.StatementProperty;
import com.sqlapp.data.schemas.properties.ValidProperty;
import com.sqlapp.data.schemas.properties.VirtualProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * 名称をプロパティとして持つ抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractNamedObject<T extends AbstractNamedObject<T>>
		extends AbstractDbObject<T> implements Serializable, Cloneable, NameProperty<T>, DisplayNameProperty<T>,
		DefinitionProperty<T>, StatementProperty<T>, ErrorMessagesProperty<T>, RemarksProperty<T>, DisplayRemarksProperty<T>
	, VirtualProperty<T>
	, ValidProperty<T>
	, CaseSensitiveProperty<T>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 210101856540540272L;
	/** name */
	private String name = null;
	/** displayName */
	private String displayName = null;
	/** description */
	private String displayRemarks = null;
	/** DBコメント */
	private String remarks = null;
	/** 定義(DDLなど) */
	private List<String> definition = null;
	/** 定義(SELECT文など) */
	private List<String> statement = null;
	/** エラーメッセージ */
	private List<String> errorMessages = null;
	private boolean caseSensitive=(Boolean)SchemaProperties.CASE_SENSITIVE.getDefaultValue();
	/** ステータス */
	private boolean valid = (Boolean)SchemaProperties.VALID.getDefaultValue();
	/** virtual */
	private boolean virtual = (Boolean)SchemaProperties.VIRTUAL.getDefaultValue();
	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractNamedObject() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractNamedObject(String name) {
		setName(name);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 * @param specificName
	 */
	protected AbstractNamedObject(String name, String specificName) {
		this.name = trim(name).intern();
		if (hasSpecificNameProperty()){
			toSpecificNameProperty().setSpecificName(specificName);
		}
	}
	
	protected boolean hasSpecificNameProperty(){
		return this instanceof SpecificNameProperty;
	}

	protected SpecificNameProperty<?> toSpecificNameProperty(){
		return ((SpecificNameProperty<?>)this);
	}
	
	protected String getSpecificName(){
		return this.getName();
	}

	protected T setSpecificName(String specificName){
		return instance();
	}

	/**
	 * 大文字小文字を区別するかを判定する
	 * 
	 */
	@Override
	public boolean isCaseSensitive() {
		if (this.getParent() == null) {
			return this.caseSensitive;
		}
		if (this.getParent() instanceof CaseSensitiveProperty){
			return ((CaseSensitiveProperty<?>)this.getParent()).isCaseSensitive();
		}
		return this.caseSensitive;
	}

	
	@Override
	public T setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive=caseSensitive;
		return instance();
	}

	/**
	 * 名称
	 * 
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * 名称を設定します
	 * 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T setName(String name) {
		if (name == null) {
			this.name = null;
			return (T) (this);
		}
		this.name = trim(name).intern();
		DbCommonObject<?> parent=getParent();
		if (parent != null&&parent instanceof AbstractDbObjectCollection) {
			synchronized (parent) {
				((AbstractDbObjectCollection<?>) parent).renew();
			}
		}
		return instance();
	}

	/**
	 * @return the displayName
	 */
	@Override
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @param displayName the displayName to set
	 */
	@Override
	public T setDisplayName(String displayName) {
		this.displayName = displayName;
		return instance();
	}
	
	/**
	 * @return the displayRemarks
	 */
	@Override
	public String getDisplayRemarks() {
		return this.displayRemarks;
	}

	/**
	 * @param displayRemarks the displayRemarks to set
	 */
	@Override
	public T setDisplayRemarks(String displayRemarks) {
		this.displayRemarks = displayRemarks;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder(!isCaseSensitive());
		builder.append(this.getName());
		builder.append(getSpecificName());
		return builder.hashCode();
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
		if (!(obj instanceof AbstractNamedObject)) {
			return false;
		}
		T val = cast(obj);
		if (!equals(SchemaProperties.NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DISPLAY_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DISPLAY_REMARKS, val, equalsHandler)) {
			return false;
		}
		if (hasSpecificNameProperty()){
			if (!equals(SchemaProperties.SPECIFIC_NAME, val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(SchemaProperties.VALID, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.VIRTUAL, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.REMARKS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.STATEMENT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFINITION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ERROR_MESSAGES, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (!equals(obj, IncludeFilterEqualsHandler.EQUALS_NAME_HANDLER)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		builder.add(SchemaProperties.DISPLAY_NAME.getLabel(), this.getDisplayName());
		if (hasSpecificNameProperty()){
			if (!eq(this.getName(), toSpecificNameProperty().getSpecificName())) {
				builder.add(SchemaProperties.SPECIFIC_NAME.getLabel(), toSpecificNameProperty().getSpecificName());
			}
		}
		toString(builder);
		return builder.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		builder.add(SchemaProperties.DISPLAY_NAME.getLabel(), this.getDisplayName());
		if (!CommonUtils.eq(this.getName(), this.getSpecificName())&&this.getSpecificName()!=null){
			builder.add(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
		}
		return builder.toString();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	protected abstract void toStringDetail(ToStringBuilder builder);

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		toStringDetail(builder);
		super.toString(builder);
		if (!isValid()) {
			builder.add(SchemaProperties.VALID, isValid());
		}
		builder.add(SchemaProperties.REMARKS, getRemarks());
		builder.add(SchemaProperties.DISPLAY_REMARKS, this.getDisplayRemarks());
		builder.add(SchemaProperties.STATEMENT, getStatement());
		builder.add(SchemaProperties.DEFINITION, getDefinition());
		builder.add(SchemaProperties.VIRTUAL, isVirtual());
		builder.add(SchemaProperties.ERROR_MESSAGES, getErrorMessages());
	}

	/**
	 * @return the remarks
	 */
	@Override
	public String getRemarks() {
		return remarks;
	}

	/**
	 * @param remarks
	 *            the remarks to set
	 */
	@Override
	public T setRemarks(String remarks) {
		this.remarks = CommonUtils.emptyToNull(remarks);
		return instance();
	}

	/**
	 * @return the errorMessages
	 */
	@Override
	public List<String> getErrorMessages() {
		if (errorMessages == null) {
			errorMessages = CommonUtils.list();
		}
		return errorMessages;
	}

	/**
	 * @return the errorMessages
	 */
	@Override
	public T setErrorMessages(List<String> errorMessages) {
		this.errorMessages = errorMessages;
		return instance();
	}

	/**
	 * @return the valid
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * @param valid
	 *            the valid to set
	 */
	public T setValid(boolean valid) {
		this.valid = valid;
		return instance();
	}

	/**
	 * @return the virtual
	 */
	public boolean isVirtual() {
		return virtual;
	}

	/**
	 * @param virtual the virtual to set
	 */
	public T setVirtual(boolean virtual) {
		this.virtual = virtual;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DefinitionProperty#getDefinition()
	 */
	@Override
	public List<String> getDefinition() {
		if (definition == null) {
			this.definition = CommonUtils.list();
		}
		return this.definition;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DefinitionProperty#setDefinition(java.util.List)
	 */
	@Override
	public T setDefinition(List<String> definition) {
		this.definition = definition;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DefinitionProperty#getStatement()
	 */
	@Override
	public List<String> getStatement() {
		if (statement == null) {
			this.statement = CommonUtils.list();
		}
		return this.statement;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DefinitionProperty#setStatement(java.util.List)
	 */
	@Override
	public T setStatement(List<String> statement) {
		this.statement = statement;
		return instance();
	}

	/**
	 * 名称のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeName(StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), getName());
		stax.writeAttribute(SchemaProperties.DISPLAY_NAME.getLabel(), getDisplayName());
		if (!eq(getName(), getSpecificName())) {
			stax.writeAttribute(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
		}
	}

	/**
	 * 共通属性要素のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeCommonAttribute(StaxWriter stax)
			throws XMLStreamException {
		super.writeCommonAttribute(stax);
		if (!this.isValid()){
			stax.writeAttribute(SchemaProperties.VALID.getLabel(), this.isValid());
		}
		if (isVirtual()) {
			stax.writeAttribute(SchemaProperties.VIRTUAL.getLabel(), this.isVirtual());
		}
		if (!needsEscape(this.getRemarks())) {
			stax.writeAttribute(SchemaProperties.REMARKS.getLabel(), this.getRemarks());
		}
		if (!needsEscape(this.getDisplayRemarks())) {
			stax.writeAttribute(SchemaProperties.DISPLAY_REMARKS.getLabel(), this.getDisplayRemarks());
		}
	}

	/**
	 * 共通値のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeCommonValue(StaxWriter stax) throws XMLStreamException {
		if (!isEmpty(this.getStatement())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.STATEMENT.getLabel(), listToString(this.getStatement()));
		}
		if (!isEmpty(this.getDefinition())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.DEFINITION.getLabel(), listToString(this.getDefinition()));
		}
		if (needsEscape(this.getRemarks())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.REMARKS.getLabel(), this.getRemarks());
		}
		if (needsEscape(this.getDisplayRemarks())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.DISPLAY_REMARKS.getLabel(), this.getDisplayRemarks());
		}
		if (!isEmpty(this.getErrorMessages())) {
			stax.newLine();
			stax.indent();
			stax.writeElement(SchemaProperties.ERROR_MESSAGES.getLabel(), this.getErrorMessages());
		}
		super.writeCommonValue(stax);
	}

	@Override
	public int compareTo(T o) {
		if (o == null) {
			return 1;
		}
		int ret = CommonUtils.compare(this.getName(), o.getName());
		if (ret != 0) {
			return ret;
		}
		ret = CommonUtils.compare(this.getSpecificName(), o.getSpecificName());
		if (ret != 0) {
			return ret;
		}
		ret = CommonUtils.compare(this.getDisplayName(), o.getDisplayName());
		return ret;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected AbstractNamedObjectXmlReaderHandler<T> getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler(this.newInstance()) {
		};
	}
}
