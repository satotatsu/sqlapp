package com.sqlapp.iterable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class VirtualThreadIterable<T> implements Iterable<T> {

	private Consumer<BlockingQueue<Object>> producer;
	private int queueSize;

	@SuppressWarnings("unchecked")
	public VirtualThreadIterable(Consumer<BlockingQueue<T>> producer, final int queueSize) {
		final Object obj = producer;
		this.producer = (Consumer<BlockingQueue<Object>>) obj;
		this.queueSize = queueSize;
	}

	public VirtualThreadIterable(Consumer<BlockingQueue<T>> producer) {
		this(producer, 20000);
	}

	enum EndMarker {
		INSTANCE
	}

	@Override
	public Iterator<T> iterator() {
		final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(queueSize);
		Thread producerThread = Thread.ofVirtual().start(() -> {
			try {
				producer.accept(queue);
			} catch (Throwable e) {
				try {
					queue.put(e);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			} finally {
				try {
					queue.put(EndMarker.INSTANCE);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
		});
		return new VirtualThreadIterator<T>(queue, producerThread);
	}

	static class VirtualThreadIterator<T> implements Iterator<T>, AutoCloseable {
		private final BlockingQueue<Object> queue;
		private Thread producerThread;
		private T next;

		VirtualThreadIterator(final BlockingQueue<Object> queue, Thread producerThread) {
			this.queue = queue;
			this.producerThread = producerThread;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean hasNext() {
			if (next != null) {
				return true;
			}
			Object obj;
			try {
				obj = queue.take();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if (obj == EndMarker.INSTANCE) {
				return false;
			}
			if (obj instanceof Throwable ex) {
				throw new RuntimeException(ex);
			}
			next = (T) obj;
			return true;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			T result = next;
			next = null;
			return result;
		}

		@Override
		public void close() throws Exception {
			producerThread.interrupt();
		}
	}
}
