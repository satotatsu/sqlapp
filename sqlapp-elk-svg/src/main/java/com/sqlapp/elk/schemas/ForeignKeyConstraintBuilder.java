/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.schemas;

import java.util.function.Function;

import org.eclipse.elk.graph.ElkLabel;

import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.elk.util.SVGTextBuilder;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForeignKeyConstraintBuilder {

	private ForeignKeyConstraintBuilder() {
	}

	public static ForeignKeyConstraintBuilder create() {
		ForeignKeyConstraintBuilder builder = new ForeignKeyConstraintBuilder();
		return builder;
	}

	public static ForeignKeyConstraintBuilder createSimple() {
		ForeignKeyConstraintBuilder builder = new ForeignKeyConstraintBuilder();
		builder.setName(f -> null);
		builder.setCascadeRule(f -> null);
		// builder.setVirtual(f -> null);
		return builder;
	}

	private Function<ForeignKeyConstraint, String> name = fk -> fk.getName();

	private Function<ForeignKeyConstraint, String> virtual = (fk -> {
		if (fk.isVirtual()) {
			return "Virtual";
		}
		return null;
	});

	private Function<ForeignKeyConstraint, String> cascadeRule = fk -> {
		final StringBuilder builder = new StringBuilder();
		if (fk.getDeleteRule() != null && !fk.getDeleteRule().isRestrict()) {
			builder.append("(");
			builder.append("DEL=");
			builder.append(fk.getDeleteRule().getAbbrName());
		}
		if (fk.getUpdateRule() != null && !fk.getUpdateRule().isRestrict()) {
			if (builder.length() > 0) {
				builder.append(",");
			} else {
				builder.append("(");
			}
			builder.append("UPD=");
			builder.append(fk.getUpdateRule().getAbbrName());
		}
		if (builder.length() > 0) {
			builder.append(")");
		}
		return builder.toString();
	};

	public String build(ForeignKeyConstraint obj) {
		StringBuilder builder = new StringBuilder();
		createName(builder, obj);
		createCascadeRule(builder, obj);
		createVirtual(builder, obj);
		return builder.toString();
	}

	private void append(StringBuilder builder, String value) {
		if (CommonUtils.isEmpty(value)) {
			return;
		}
		if (builder.length() > 0) {
			builder.append("\n");
		}
		builder.append(value);
	}

	private void createName(StringBuilder builder, ForeignKeyConstraint obj) {
		String value = name.apply(obj);
		append(builder, value);
	}

	private void createCascadeRule(StringBuilder builder, ForeignKeyConstraint obj) {
		String value = cascadeRule.apply(obj);
		append(builder, value);
	}

	private void createVirtual(StringBuilder builder, ForeignKeyConstraint obj) {
		String value = virtual.apply(obj);
		append(builder, value);
	}

	public SVGTextBuilder setText(ElkLabel label, String text) {
		String[] args = text.split("\n");
		SVGTextBuilder builder = new SVGTextBuilder(args);
		String svgText = builder.getText();
		label.setText(svgText);
		// サイズを必ず設定
		label.setWidth(builder.getMaxLength() * 7.0);
		label.setHeight(builder.getCount() * 12.0);
		return builder;
	}
}
