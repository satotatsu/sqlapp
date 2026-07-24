/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.jdbc.sql;

import java.io.Closeable;
import java.util.List;
import java.util.stream.Stream;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

import lombok.Getter;

@Getter
public class BindParameterHolder implements Closeable {

	private final BindParameter bindParameter;
	private final List<BindParameter> bindParameters;

	public BindParameterHolder(BindParameter bindParameter) {
		this.bindParameter = bindParameter;
		this.bindParameters = null;
	}

	public BindParameterHolder() {
		this.bindParameter = null;
		this.bindParameters = CommonUtils.list();
	}

	public Stream<BindParameter> stream() {
		if (bindParameter != null) {
			return Stream.of(bindParameter);
		}
		return bindParameters.stream();
	}

	@Override
	public void close() {
		if (bindParameter != null) {
			bindParameter.close();
			return;
		}
		for (BindParameter parameter : bindParameters) {
			parameter.close();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder();
		if (bindParameter != null) {
			builder.add("bindParameter", bindParameter);
		}
		if (bindParameters != null) {
			final SeparatedStringBuilder sep = new SeparatedStringBuilder(",");
			bindParameters.forEach(val -> {
				sep.add(val);
			});
			builder.add("bindParameters", sep.toString());
		}
		return builder.toString();
	}
}
