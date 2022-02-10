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

package com.sqlapp.data.schemas.properties;

import java.util.Collection;
import java.util.Set;

import com.sqlapp.util.CommonUtils;

/**
 * EventManipulation IF
 * @author satoh
 *
 */
public interface EventManipulationProperty<T>{

	Set<String> getEventManipulation();

	T setEventManipulation(Set<String> value);

	T setEventManipulation(String value);

	@SuppressWarnings("unchecked")
	default T addEventManipulation(String... texts) {
		if (this.getEventManipulation() == null) {
			this.setEventManipulation(CommonUtils.upperTreeSet());
		}
		if (texts==null){
			return (T)this;
		}
		for (String text : texts) {
			this.getEventManipulation().add(text);
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	default T addEventManipulation(Collection<String> texts) {
		if (this.getEventManipulation() == null) {
			this.setEventManipulation(CommonUtils.upperTreeSet());
		}
		if (texts==null){
			return (T)this;
		}
		for (String text : texts) {
			this.getEventManipulation().add(text);
		}
		return (T)this;
	}


}
