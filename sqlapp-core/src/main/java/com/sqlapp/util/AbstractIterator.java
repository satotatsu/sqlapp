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

package com.sqlapp.util;

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.EvalExecutor;

/**
 * リスト、配列の繰り返し処理を行うためのクラス
 * 
 * @author satoh
 *
 * @param <T>
 */
public abstract class AbstractIterator<T> {

	public AbstractIterator() {
	}

	public AbstractIterator(int step) {
		this.step = step;
	}

	public AbstractIterator(int step, boolean callLastStep) {
		this.step = step;
		this.callLastStep = callLastStep;
	}

	public AbstractIterator(CachedEvaluator evaluator, String expression) {
		this.evaluator = evaluator;
		this.expression = expression;
	}

	public AbstractIterator(CachedEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	public AbstractIterator(CachedEvaluator evaluator, String expression,
			int step, boolean callLastStep) {
		this.evaluator = evaluator;
		this.expression = expression;
		this.step = step;
		this.callLastStep = callLastStep;
	}

	private int step = 0;

	private boolean callLastStep = true;

	private String expression = null;

	private CachedEvaluator evaluator = null;

	public AbstractIterator<T> execute(Object val) throws Exception {
		try {
			doExecute(val);
			return this;
		} finally {
			executeFinally();
		}
	}

	public AbstractIterator<T> execute(List<?> list) throws Exception {
		try {
			iterateList(list);
			return this;
		} finally {
			executeFinally();
		}
	}

	public AbstractIterator<T> execute(Iterable<?> iterable) throws Exception {
		try {
			iterateCollection(iterable);
			return this;
		} finally {
			executeFinally();
		}
	}

	public AbstractIterator<T> execute(Enumeration<?> enumeration)
			throws Exception {
		try {
			iterateEnumeration(enumeration);
			return this;
		} finally {
			executeFinally();
		}
	}

	protected void doExecute(Object val) throws Exception {
		if (val == null) {
			return;
		} else if (val instanceof List<?>) {
			List<?> list = (List<?>) val;
			iterateList(list);
		} else if (val instanceof Iterable<?>) {
			Iterable<?> c = (Iterable<?>) val;
			execute(c);
		} else if (val instanceof Enumeration<?>) {
			Enumeration<?> c = (Enumeration<?>) val;
			execute(c);
		} else if (val instanceof Map<?, ?>) {
			Map<?, ?> c = (Map<?, ?>) val;
			execute(c.entrySet());
		} else if (val.getClass().isArray()) {
			Class<?> compClass = val.getClass().getComponentType();
			if (compClass.isPrimitive()) {
				iterateArray(val);
			} else {
				Object[] arr = (Object[]) val;
				iterateArray(arr);
			}
		} else {
			handle(eval(val), 0);
		}
	}

	@SuppressWarnings("unchecked")
	private T eval(Object val) {
		if (evaluator != null && expression != null) {
			return eval(evaluator.getEvalExecutor(expression), val);
		} else {
			return (T) val;
		}
	}

	@SuppressWarnings("unchecked")
	private T eval(EvalExecutor evalExecutor, Object val) {
		if (val == null) {
			return null;
		}
		Converter<?> con = Converters.getDefault().getConverter(val.getClass());
		if (con == null) {
			return (T) evalExecutor.eval(val);
		}
		return (T) val;
	}

	protected void handleException(Exception e) {
		if (e instanceof RuntimeException) {
			throw (RuntimeException) e;
		}
		throw new RuntimeException(e);
	}

	protected void executeFinally() {
	}

	protected void iterateList(List<?> list) throws Exception {
		int length = list.size();
		for (int i = 0; i < length; i++) {
			Object val = list.get(i);
			handle(eval(val), i);
			callStepHandle(i);
		}
		callStepLast(length);
	}

	protected void iterateCollection(Iterable<?> args) throws Exception {
		int i = 0;
		for (Object val : args) {
			handle(eval(val), i);
			callStepHandle(i);
			i++;
		}
		callStepLast(i);
	}

	protected void iterateEnumeration(Enumeration<?> args) throws Exception {
		int i = 0;
		while (args.hasMoreElements()) {
			handle(eval(args.nextElement()), i);
			callStepHandle(i);
			i++;
		}
		callStepLast(i);
	}

	protected long iterateArray(Object[] arr) throws Exception {
		long count = 0;
		int length = arr.length;
		for (int i = 0; i < length; i++) {
			handle(eval(arr[i]), i);
			callStepHandle(i);
		}
		callStepLast(length);
		return count;
	}

	protected long iterateArray(Object arr) throws Exception {
		long count = 0;
		int length = Array.getLength(arr);
		for (int i = 0; i < length; i++) {
			handle(eval(Array.get(arr, i)), i);
			callStepHandle(i);
		}
		callStepLast(length);
		return count;
	}

	protected void callStepHandle(int i) throws Exception {
		if (step == 0 || ((i + 1) % step) != 0) {
			return;
		}
		stepHandle(i / step, step);
	}

	protected void callStepLast(int length) throws Exception {
		if (step == 0 || !callLastStep) {
			return;
		}
		if (((length) % step) == 0) {
			return;
		}
		stepHandle(length / step, (length) % step);
	}

	protected abstract void handle(T obj, int index) throws Exception;

	protected void stepHandle(int index, int stepSize) throws Exception {

	}

}
