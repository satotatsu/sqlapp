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

import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Timestamp;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DefinerProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.EndsProperty;
import com.sqlapp.data.schemas.properties.EventTypeProperty;
import com.sqlapp.data.schemas.properties.ExecuteAtProperty;
import com.sqlapp.data.schemas.properties.IntervalFieldProperty;
import com.sqlapp.data.schemas.properties.IntervalValueProperty;
import com.sqlapp.data.schemas.properties.LastExecutedProperty;
import com.sqlapp.data.schemas.properties.OnCompletionProperty;
import com.sqlapp.data.schemas.properties.StartsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * イベントに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class Event extends AbstractSchemaObject<Event> implements
		HasParent<EventCollection>, DefinerProperty<Event>, EventTypeProperty<Event>
	, IntervalValueProperty<Event>
	, IntervalFieldProperty<Event>
	, OnCompletionProperty<Event>
	, StartsProperty<Event>
	, EndsProperty<Event>
	, ExecuteAtProperty<Event>
	, LastExecutedProperty<Event>
	, EnableProperty<Event> {
	/** serialVersionUID */
	private static final long serialVersionUID = 2944673540434794114L;
	/** 実行するユーザー */
	private String definer = null;
	/** 繰り返し回数 */
	private Integer intervalValue = null;
	/**
	 * 繰り返しフィールド(YEAR,QUARTER,DAY...)
	 * <p>
	 * YEAR | QUARTER | MONTH | DAY | HOUR | MINUTE | WEEK | SECOND | YEAR_MONTH
	 * | DAY_HOUR | DAY_MINUTE | DAY_SECOND | HOUR_MINUTE | HOUR_SECOND |
	 * MINUTE_SECOND
	 * </p>
	 */
	private String intervalField = null;
	/** eventType */
	private EventType eventType = null;
	/** onCompletion */
	private String onCompletion = null;
	/** 開始時刻 */
	private Timestamp starts = null;
	/** 終了時刻 */
	private Timestamp ends = null;
	/** 実行時間 */
	private Timestamp executeAt = null;
	/** 最終実行時間 */
	private Timestamp lastExecuted = null;
	/** イベントが有効かを表す */
	private boolean enable =  (Boolean)SchemaProperties.ENABLE.getDefaultValue();

	public Event() {
	}

	public Event(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Event> newInstance(){
		return ()->new Event();
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
		if (!(obj instanceof Event)) {
			return false;
		}
		Event val = (Event) obj;
		if (!equals(SchemaProperties.DEFINER, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.EVENT_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INTERVAL_FIELD, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INTERVAL_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ON_COMPLETION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.STARTS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ENDS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.EXECUTE_AT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LAST_EXECUTED, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ENABLE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.DEFINER.getLabel(), this.getDefiner());
		stax.writeAttribute(SchemaProperties.INTERVAL_FIELD.getLabel(), this.getIntervalField());
		stax.writeAttribute(SchemaProperties.INTERVAL_VALUE.getLabel(), this.getIntervalValue());
		stax.writeAttribute(SchemaProperties.EVENT_TYPE.getLabel(), this.getEventType());
		stax.writeAttribute(SchemaProperties.ON_COMPLETION.getLabel(), this.getOnCompletion());
		stax.writeAttribute(SchemaProperties.STARTS.getLabel(), this.getStarts());
		stax.writeAttribute(SchemaProperties.ENDS.getLabel(), this.getEnds());
		stax.writeAttribute(SchemaProperties.EXECUTE_AT.getLabel(), this.getExecuteAt());
		stax.writeAttribute(SchemaProperties.LAST_EXECUTED.getLabel(), this.getLastExecuted());
		if (!this.isEnable()){
			stax.writeAttribute(SchemaProperties.ENABLE.getLabel(), this.isEnable());
		}
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DEFINER, this.getDefiner());
		builder.add(SchemaProperties.INTERVAL_FIELD, this.getIntervalField());
		builder.add(SchemaProperties.INTERVAL_VALUE, this.getIntervalValue());
		builder.add(SchemaProperties.EVENT_TYPE, this.getEventType());
		builder.add(SchemaProperties.ON_COMPLETION, this.getOnCompletion());
		builder.add(SchemaProperties.STARTS, this.getStarts());
		builder.add(SchemaProperties.ENDS, this.getEnds());
		builder.add(SchemaProperties.EXECUTE_AT, this.getExecuteAt());
		builder.add(SchemaProperties.LAST_EXECUTED, this.getLastExecuted());
		builder.add(SchemaProperties.ENABLE, this.isEnable());
	}

	@Override
	public EventCollection getParent() {
		return (EventCollection) super.getParent();
	}

	/**
	 * @return the definer
	 */
	public String getDefiner() {
		return definer;
	}

	/**
	 * @param definer
	 *            the definer to set
	 */
	public Event setDefiner(String definer) {
		this.definer = definer;
		return instance();
	}

	/**
	 * @return the eventType
	 */
	public EventType getEventType() {
		return eventType;
	}

	/**
	 * @param eventType the eventType to set
	 */
	public Event setEventType(EventType eventType) {
		this.eventType = eventType;
		return instance();
	}

	/**
	 * @return the intervalValue
	 */
	@Override
	public Integer getIntervalValue() {
		return intervalValue;
	}

	@Override
	public Event setIntervalValue(int intervalValue) {
		this.intervalValue = intervalValue;
		return instance();
	}

	@Override
	public Event setIntervalValue(Number intervalValue) {
		if (intervalValue!=null){
			this.intervalValue = intervalValue.intValue();
		} else{
			this.intervalValue = null;
		}
		return instance();
	}

	/**
	 * @return the intervalField
	 */
	@Override
	public String getIntervalField() {
		return intervalField;
	}

	/**
	 * @param intervalField
	 *            the intervalField to set
	 */
	@Override
	public Event setIntervalField(String intervalField) {
		this.intervalField = trim(intervalField);
		return instance();
	}

	/**
	 * @return the onCompletion
	 */
	@Override
	public String getOnCompletion() {
		return onCompletion;
	}

	/**
	 * @param onCompletion
	 *            the onCompletion to set
	 */
	@Override
	public Event setOnCompletion(String onCompletion) {
		this.onCompletion = trim(onCompletion);
		return this;
	}

	/**
	 * @return the start
	 */
	@Override
	public Timestamp getStarts() {
		return starts;
	}

	/**
	 * @param starts
	 *            the starts to set
	 */
	@Override
	public Event setStarts(Timestamp starts) {
		this.starts = starts;
		return instance();
	}

	/**
	 * @return the end
	 */
	@Override
	public Timestamp getEnds() {
		return ends;
	}

	/**
	 * @param ends
	 *            the ends to set
	 */
	@Override
	public Event setEnds(Timestamp ends) {
		this.ends = ends;
		return this;
	}

	/**
	 * @return the executeAt
	 */
	@Override
	public Timestamp getExecuteAt() {
		return executeAt;
	}

	/**
	 * @param executeAt
	 *            the executeAt to set
	 */
	@Override
	public Event setExecuteAt(Timestamp executeAt) {
		this.executeAt = executeAt;
		return instance();
	}

	/**
	 * @return the lastExecuted
	 */
	@Override
	public Timestamp getLastExecuted() {
		return lastExecuted;
	}

	/**
	 * @param lastExecuted
	 *            the lastExecuted to set
	 */
	@Override
	public Event setLastExecuted(Timestamp lastExecuted) {
		this.lastExecuted = lastExecuted;
		return instance();
	}

	/**
	 * @return the enable
	 */
	public boolean isEnable() {
		return enable;
	}

	/**
	 * @param enable
	 *            the enable to set
	 */
	public Event setEnable(boolean enable) {
		this.enable = enable;
		return instance();
	}

}
