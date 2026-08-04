/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.iterable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class VirtualThreadIterable<T> implements Iterable<T> {

	private final Consumer<Consumer<T>> producer;
	private final Runnable finalizer;
	private final int queueSize;

	public VirtualThreadIterable(Consumer<Consumer<T>> producer, Runnable finalizer, int queueSize) {
		this.producer = Objects.requireNonNull(producer, "producer");
		this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
		if (queueSize <= 0) {
			throw new IllegalArgumentException("queueSize must be greater than zero: " + queueSize);
		}
		this.queueSize = queueSize;
	}

	public VirtualThreadIterable(Consumer<Consumer<T>> producer) {
		this(producer, () -> {
		}, 20000);
	}

	@Override
	public Iterator<T> iterator() {
		BlockingQueue<Message<T>> queue = new ArrayBlockingQueue<>(queueSize);

		Thread producerThread = Thread.ofVirtual().start(() -> {
			try {
				producer.accept(value -> {
					try {
						queue.put(new Value<>(value));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new ProducerInterruptedException(e);
					}
				});
			} catch (ProducerInterruptedException e) {
				// Consumerが途中終了してProducerをinterruptしたケース
			} catch (Throwable e) {
				offerTerminalMessage(queue, new Failure<>(e));
			} finally {
				try {
					finalizer.run();
				} catch (Throwable e) {
					offerTerminalMessage(queue, new Failure<>(e));
				} finally {
					offerTerminalMessage(queue, End.instance());
				}
			}
		});

		return new VirtualThreadIterator<>(queue, producerThread);
	}

	private static <T> void offerTerminalMessage(BlockingQueue<Message<T>> queue, Message<T> message) {
		/*
		 * Producerが正常に完了した場合、Consumerはキューを消費中なので、 通常はputで問題ない。
		 */
		boolean interrupted = false;
		for (;;) {
			try {
				queue.put(message);
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private sealed interface Message<T> permits Value, Failure, End {
	}

	private record Value<T>(T value) implements Message<T> {
	}

	private record Failure<T>(Throwable cause) implements Message<T> {
	}

	private enum End implements Message<Object> {
		INSTANCE;

		@SuppressWarnings("unchecked")
		static <T> Message<T> instance() {
			return (Message<T>) INSTANCE;
		}
	}

	private static final class ProducerInterruptedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		ProducerInterruptedException(InterruptedException cause) {
			super(cause);
		}
	}

	private static final class VirtualThreadIterator<T> implements Iterator<T>, AutoCloseable {

		private final BlockingQueue<Message<T>> queue;
		private final Thread producerThread;

		private T next;
		private boolean nextReady;
		private boolean finished;
		private boolean closed;

		VirtualThreadIterator(BlockingQueue<Message<T>> queue, Thread producerThread) {
			this.queue = queue;
			this.producerThread = producerThread;
		}

		@Override
		public boolean hasNext() {
			if (nextReady) {
				return true;
			}
			if (finished) {
				return false;
			}

			final Message<T> message;
			try {
				message = queue.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				close();
				throw new RuntimeException(e);
			}

			if (message instanceof Value<T> value) {
				next = value.value();
				nextReady = true;
				return true;
			}

			if (message instanceof Failure<T> failure) {
				finished = true;
				close();
				throw propagate(failure.cause());
			}

			finished = true;
			return false;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			T result = next;
			next = null;
			nextReady = false;
			return result;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			finished = true;
			next = null;
			nextReady = false;
			producerThread.interrupt();
		}

		private static RuntimeException propagate(Throwable cause) {
			if (cause instanceof RuntimeException e) {
				return e;
			}
			if (cause instanceof Error e) {
				throw e;
			}
			return new RuntimeException(cause);
		}
	}
}