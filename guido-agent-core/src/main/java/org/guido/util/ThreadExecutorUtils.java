package org.guido.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public abstract class ThreadExecutorUtils {
	
	public static ExecutorService newFixedThreadPool(int poolSize) {
		return Executors.newFixedThreadPool(poolSize, 
			new ThreadFactory() {
		        public Thread newThread(Runnable r) {
		            Thread thread = Executors.defaultThreadFactory().newThread(r);
		            thread.setDaemon(true);
		            return thread;
		        }
		 });
	}

	public static ExecutorService newSingleThreadExecutor() {
		return Executors.newSingleThreadExecutor(
			new ThreadFactory() {
		        public Thread newThread(Runnable r) {
		            Thread thread = Executors.defaultThreadFactory().newThread(r);
		            thread.setDaemon(true);
		            return thread;
		        }
		});
	}
}
