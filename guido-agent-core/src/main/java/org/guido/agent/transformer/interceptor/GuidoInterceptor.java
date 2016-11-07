package org.guido.agent.transformer.interceptor;

import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_ALLOWED;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_SHORT_SIGNATURE;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_THRESHOLD;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.javassist.CtMethod;

public class GuidoInterceptor {
	
	static public List<Object[]> references;
	static public Deque<Object[]> queue;
	public static String pid;
	public static long threshold = 500000; // (0.5 ms)
	static public Map<String, String> extraProps;
	
	public static final int MAX_STACK_DEPTH = 128;
	
	static long totalsent = 0;
	static long totalerror = 0;
	
	public GuidoInterceptor() {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GuidoInterceptor(Object mapRef) {
		Map<String, Object> paramMap = (Map<String, Object>)mapRef;
		GuidoInterceptor.references = (List<Object[]>)paramMap.get("refs");
		GuidoInterceptor.queue = (Deque)paramMap.get("logQ");
		GuidoInterceptor.pid = (String)paramMap.get("pid");
		GuidoInterceptor.threshold = (long)paramMap.get("threshold");
		GuidoInterceptor.extraProps = (Map<String, String>)paramMap.get("extraprops");
	}
	
	static public Class<?>[] toLoad = new Class<?>[] {
		InThreadStackElement.class,
		SimpleOpInteger.class,
		GuidoLogger.class,
		ReferenceIndex.class
	};
	
	static ThreadLocal<String> threadUuid = new ThreadLocal<String>();
	static ThreadLocal<InThreadStackElement[]> localRefStack = new ThreadLocal<InThreadStackElement[]>();
	static ThreadLocal<SimpleOpInteger> positionInStack = new ThreadLocal<SimpleOpInteger>();
	
	static public String insertBefore(int index) {
		return String
				.format(
				"{ org.guido.agent.transformer.interceptor.GuidoInterceptor.push(%d); }",
				index
				);
	}
	
	static public String insertAfter() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.pop(); }";
	}
	
	public static String addCatch() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.popInError($e); throw $e; }";
	}

	public static String insertDebugBefore(CtMethod method) {
		return "{ System.out.println(\" >>> \" + Thread.currentThread().getContextClassLoader()); }";
	}

	static public void push(int index) {
		InThreadStackElement[] stack = localRefStack.get();
		if(stack == null) {
			initTLSElements();
		}
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			localRefStack.get()[positionInStack.get().addAndGet()].start(references.get(index));
		}
	}
	
	static private boolean passes(InThreadStackElement stackElement) {
		return stackElement.deltaTime > (long)stackElement.reference[REF_THRESHOLD] && (Boolean)stackElement.reference[REF_ALLOWED];
	}
	
	static public void pop() {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(passes(stackElement)) {
				//dumpClassLoaders();
				Object[] objects = buildObjectCommon(stackElement);
				totalsent++;
				boolean offered = queue.offer(objects);
				if(!offered) {
					totalerror++;
				}
			}
		}
	}
	static private void dumpClassLoaders() {
		System.out.println(String.format("GuidoInterceptor classloader is %s ", 
							GuidoInterceptor.class.getClassLoader()));
		System.out.println(String.format("Thread class loader is %s", 
							Thread.currentThread().getContextClassLoader()));
	}
	
	static public void popInError(Throwable t) {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(passes(stackElement)) {
				Object[] objects = buildObjectCommon(stackElement);
				totalsent++;
				boolean offered = queue.offer(objects);
				if(!offered) {
					totalerror++;
				}
			}
		}
	}
	
	static private Object[] buildObjectCommon(InThreadStackElement stackElement) {
		Object[] objects = new Object[5];
		int index = 0;
		objects[index++] = pid;
		objects[index++] = threadUuid.get();
		objects[index++] = positionInStack.get().get() + 1;
		objects[index++] = stackElement.reference[REF_SHORT_SIGNATURE];
		objects[index++] = stackElement.deltaTime;
		return objects;
	}
	
	private static void initTLSElements() {
		InThreadStackElement[] stack = new InThreadStackElement[MAX_STACK_DEPTH + 1]; // should watch the stack depth at a moment or another
		for(int index = 0; index < stack.length; index++) {
			stack[index] = new InThreadStackElement();
		}
		localRefStack.set(stack);
		threadUuid.set(UUID.randomUUID().toString());
		positionInStack.set(new SimpleOpInteger());
	}
}
