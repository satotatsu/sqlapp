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
import static com.sqlapp.util.CommonUtils.first;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.trim;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ActionConditionProperty;
import com.sqlapp.data.schemas.properties.ActionOrientationProperty;
import com.sqlapp.data.schemas.properties.ActionReferenceNewRowProperty;
import com.sqlapp.data.schemas.properties.ActionReferenceOldRowProperty;
import com.sqlapp.data.schemas.properties.ActionTimingProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.EventManipulationProperty;
import com.sqlapp.data.schemas.properties.WhenProperty;
import com.sqlapp.data.schemas.properties.complex.TableProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.xml.EmptyTextSkipHandler;
import com.sqlapp.util.xml.UpperSetHandler;

/**
 * トリガー
 * 
 * @author satoh
 * 
 */
public final class Trigger extends Routine<Trigger> implements
		HasParent<TriggerCollection>, EnableProperty<Trigger>
	, TableProperty<Trigger>
	, EventManipulationProperty<Trigger>
	, ActionReferenceOldRowProperty<Trigger>
	, ActionReferenceNewRowProperty<Trigger>
	, ActionConditionProperty<Trigger>
	, ActionOrientationProperty<Trigger>
	, ActionTimingProperty<Trigger>
	, WhenProperty<Trigger>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4906000020243009507L;
	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)
	 */
	private final Set<String> eventManipulation = CommonUtils.upperTreeSet();
	/**
	 * 変更前の行を参照するキーワード OLD :OLDなど
	 */
	private String actionReferenceOldRow = null;
	/**
	 * 変更後の行を参照するキーワード NEW :NEWなど
	 */
	private String actionReferenceNewRow = null;
	/** トリガーが有効かを表す */
	private boolean enable = (Boolean)SchemaProperties.ENABLE.getDefaultValue();
	/** テーブル名 */
	private final Table table = null;
	/**
	 * トリガーの実行条件(WHEN CLAUSE)
	 */
	private String actionCondition = null;
	/**
	 * アクションの対象(ROW STATEMENT)など
	 */
	private String actionOrientation = null;
	/**
	 * トリガーとなるタイミング(BEFORE AFTERなど)
	 */
	private String actionTiming = null;
	/**
	 * トリガー条件
	 */
	private String when = null;

	/**
	 * コンストラクタ
	 */
	public Trigger() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Trigger(final String name) {
		super(name);
	}

	@Override
	protected Supplier<Trigger> newInstance(){
		return ()->new Trigger();
	}
	
	@Override
	protected void toStringDetail(final ToStringBuilder builder) {
		builder.add(SchemaProperties.EVENT_MANIPULATION, this.getEventManipulation());
		builder.add(SchemaProperties.ACTION_CONDITION, this.getActionCondition());
		builder.add(SchemaProperties.ACTION_TIMING, this.getActionTiming());
		builder.add(SchemaProperties.ACTION_ORIENTATION, this.getActionOrientation());
		builder.add(SchemaProperties.ACTION_REFERENCE_OLD_ROW, this.getActionReferenceOldRow());
		builder.add(SchemaProperties.ACTION_REFERENCE_NEW_ROW, this.getActionReferenceNewRow());
		builder.add(SchemaProperties.ENABLE, this.isEnable());
		builder.add(SchemaProperties.TABLE_SCHEMA_NAME, this.getTableSchemaName());
		builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		builder.add(SchemaProperties.WHEN, this.getWhen());
		super.toStringDetail(builder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof Trigger)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final Trigger val = cast(obj);
		if (!equals(SchemaProperties.EVENT_MANIPULATION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ACTION_CONDITION, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.ACTION_ORIENTATION, val,
				equalsHandler, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getActionOrientation(), val.getActionOrientation()))) {
			return false;
		}
		if (!equals(SchemaProperties.ACTION_TIMING, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.ACTION_REFERENCE_OLD_ROW, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getActionReferenceOldRow(), val.getActionReferenceOldRow()))) {
			return false;
		}
		if (!equals(
				SchemaProperties.ACTION_REFERENCE_NEW_ROW, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getActionReferenceNewRow(), val.getActionReferenceNewRow()))) {
			return false;
		}
		if (!equals(SchemaProperties.ENABLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.WHEN, val,	equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.Routine#writeXmlOptionalAttributes(com.sqlapp
	 * .util.StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		if (this.getEventManipulation().size() == 0) {
		} else if (this.getEventManipulation().size() == 1) {
			stax.writeAttribute(SchemaProperties.EVENT_MANIPULATION.getLabel(),
					first(this.getEventManipulation()));
		} else {
			final SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
			builder.add(this.getEventManipulation());
			stax.writeAttribute(SchemaProperties.EVENT_MANIPULATION.getLabel(), builder.toString());
		}
		stax.writeAttribute(SchemaProperties.ACTION_CONDITION.getLabel(), this.getActionCondition());
		stax.writeAttribute(SchemaProperties.ACTION_TIMING.getLabel(), this.getActionTiming());
		stax.writeAttribute(SchemaProperties.ACTION_ORIENTATION.getLabel(), this.getActionOrientation());
		stax.writeAttribute(SchemaProperties.ACTION_REFERENCE_OLD_ROW.getLabel(),
				this.getActionReferenceOldRow());
		stax.writeAttribute(SchemaProperties.ACTION_REFERENCE_NEW_ROW.getLabel(),
				this.getActionReferenceNewRow());
		if (!this.isEnable()){
			stax.writeAttribute(SchemaProperties.ENABLE.getLabel(), this.isEnable());
		}
		stax.writeAttribute(SchemaProperties.TABLE_SCHEMA_NAME.getLabel(), this.getTableSchemaName());
		stax.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), this.getTableName());
		stax.writeAttribute(SchemaProperties.WHEN.getLabel(), this.getWhen());
		super.writeXmlOptionalAttributes(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.Routine#writeXmlOptionalValues(com.sqlapp.util
	 * .StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)を取得する
	 * 
	 */
	@Override
	public Set<String> getEventManipulation() {
		return eventManipulation;
	}

	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)を設定する
	 * 
	 * @param eventManipulation
	 */
	@Override
	public Trigger setEventManipulation(final Set<String> eventManipulation) {
		this.eventManipulation.clear();
		if (eventManipulation != null) {
			this.eventManipulation.addAll(eventManipulation);
		}
		return this;
	}

	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)を設定する
	 * 
	 * @param args
	 */
	@Override
	public Trigger addEventManipulation(final String... args) {
		for (final String arg : args) {
			final String val = trim(arg);
			if (!isEmpty(val)) {
				eventManipulation.add(val);
			}
		}
		return this;
	}

	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)を設定する
	 * 
	 * @param arg
	 */
	@Override
	public Trigger setEventManipulation(String arg) {
		if (arg == null || arg.length() == 0) {
			eventManipulation.clear();
			return this;
		}
		arg = arg.toUpperCase();
		String[] vals = arg.split("\\s+OR\\s+");
		if (vals.length == 1) {
			vals = arg.split("[, |]");
		}
		return setEventManipulation(vals);
	}

	/**
	 * トリガーを起動する内容(INSERT UPDATE DELETE)を設定する
	 * 
	 * @param args
	 */
	public Trigger setEventManipulation(final String... args) {
		eventManipulation.clear();
		return this.addEventManipulation(args);
	}

	/**
	 * トリガーとなるタイミング(BEFORE AFTERなど)を取得する
	 * 
	 */
	@Override
	public String getActionTiming() {
		return actionTiming;
	}

	/**
	 * トリガーとなるタイミング(BEFORE AFTERなど)を設定する
	 * 
	 * @param actionTiming
	 */
	@Override
	public Trigger setActionTiming(final String actionTiming) {
		this.actionTiming = actionTiming;
		return this;
	}

	/**
	 * テキストの行の設定
	 * 
	 * @param lines
	 */
	@Override
	public Trigger addStatement(final String... lines) {
		for (final String line : lines) {
			if(this.getStatement()==null) {
				this.setStatement(CommonUtils.list());
			}
			addStatement(line);
		}
		return this;
	}

	/**
	 * テキストの行の設定
	 * 
	 * @param lines
	 */
	@Override
	public Trigger addStatement(final Collection<String> lines) {
		for (final String line : lines) {
			addStatement(line);
		}
		return this;
	}

	/**
	 * @return the actionOrientation
	 */
	@Override
	public String getActionOrientation() {
		return actionOrientation;
	}

	/**
	 * @param actionOrientation
	 *            the actionOrientation to set
	 */
	@Override
	public Trigger setActionOrientation(final String actionOrientation) {
		this.actionOrientation = actionOrientation;
		return instance();
	}

	/**
	 * @return the actionReferenceOldRow
	 */
	@Override
	public String getActionReferenceOldRow() {
		return actionReferenceOldRow;
	}

	/**
	 * @param actionReferenceOldRow
	 *            the actionReferenceOldRow to set
	 */
	@Override
	public Trigger setActionReferenceOldRow(final String actionReferenceOldRow) {
		this.actionReferenceOldRow = actionReferenceOldRow;
		return instance();
	}

	/**
	 * @return the actionReferenceNewRow
	 */
	@Override
	public String getActionReferenceNewRow() {
		return actionReferenceNewRow;
	}

	/**
	 * @param actionReferenceNewRow
	 *            the actionReferenceNewRow to set
	 */
	@Override
	public Trigger setActionReferenceNewRow(final String actionReferenceNewRow) {
		this.actionReferenceNewRow = actionReferenceNewRow;
		return instance();
	}

	/**
	 * @return the when
	 */
	@Override
	public String getWhen() {
		return when;
	}

	/**
	 * @param when the when to set
	 */
	@Override
	public Trigger setWhen(final String when) {
		this.when = when;
		return instance();
	}

	/**
	 * @return the active
	 */
	@Override
	public boolean isEnable() {
		return enable;
	}

	/**
	 * @param enable
	 *            the enable to set
	 */
	@Override
	public Trigger setEnable(final boolean enable) {
		this.enable = enable;
		return this;
	}

	/**
	 * @return the actionCondition
	 */
	@Override
	public String getActionCondition() {
		return actionCondition;
	}

	/**
	 * @param actionCondition
	 *            the actionCondition to set
	 */
	@Override
	public Trigger setActionCondition(final String actionCondition) {
		this.actionCondition = actionCondition;
		return this;
	}

	@Override
	public TriggerCollection getParent() {
		return (TriggerCollection) super.getParent();
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Trigger> getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler<Trigger>(this.newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				registerTransparent(SchemaProperties.EVENT_MANIPULATION.getLabel(), new UpperSetHandler(),
						new EmptyTextSkipHandler());
			}
		};
	}

	@Override
	protected void validate(){
		super.validate();
		this.setTable(table);
	}
}
