package org.guido.agent.transformer.interceptor;

import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_ALLOWED;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_CLASS_NAME;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_SHORT_SIGNATURE;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_THRESHOLD;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.javassist.CtMethod;

public class GuidoInterceptor {
	
	static public List<Object[]> references;
	static public Deque<Object[]> queue;
	public static String pid;
	public static long threshold = 500000; // (0.5 ms)
	static public Map<String, String> extraProps;
	
	public static final int MAX_STACK_DEPTH = 2047;
	
	static long totalsent = 0;
	static long totalerror = 0;
	
	public GuidoInterceptor() {
		System.out.println("GuidoInterceptor empty created by " + getClass().getClassLoader());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GuidoInterceptor(Object mapRef) {
		System.out.println("GuidoInterceptor created by " + getClass().getClassLoader());
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
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.pop($args); }";
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
			// GuidoLogger.debug("@@@ Created TLS in " + GuidoInterceptor.class.getClassLoader().toString());
			initTLSElements();
		}
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			localRefStack.get()[positionInStack.get().addAndGet()].start(references.get(index), index);
		}
	}
	
	static private boolean passes(long deltaTime, Object[] reference) {
		return deltaTime > (long)reference[REF_THRESHOLD] && (Boolean)reference[REF_ALLOWED];
	}
	
	static public void pop(Object[] args) {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(passes(stackElement.deltaTime, stackElement.reference)) {
				Object[] objects = buildObjectCommon(stackElement.deltaTime, stackElement.reference);
				//(long)stackElement.reference[ReferenceIndex.REF_COUNT] += 1;
				//StringBuffer sb = buildCommon(stackElement.deltaTime, stackElement.reference);
				totalsent++;
				boolean offered = queue.offer(objects);
				if(!offered) {
					totalerror++;
				}
			}
		}
	}
	
	static private Object[] buildObjectCommon(long deltaTime, Object[] reference) {
		Object[] objects = new Object[7];
		objects[0] = reference[REF_CLASS_NAME];
		objects[1] = pid;
		objects[2] = threadUuid.get();
		objects[3] = positionInStack.get().get() + 1;
		objects[4] = reference[REF_SHORT_SIGNATURE];
		objects[5] = deltaTime;
		return objects;
	}
	
	static private StringBuffer buildCommon(long deltaTime, Object[] reference) {
		return new StringBuffer(2048)
				.append(reference[REF_CLASS_NAME])
				.append(" - pid=").append(pid)
				.append(" threadUuid=").append(threadUuid.get())
				.append(" depth=").append(positionInStack.get().get() + 1)
				.append(" methodCalled=").append(reference[REF_SHORT_SIGNATURE])
				.append(" durationInNS=").append(deltaTime);
	}

	static public void popInError(Throwable t) {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(passes(stackElement.deltaTime, stackElement.reference)) {
//				StringBuffer sb = buildCommon(stackElement.deltaTime, stackElement.reference)
//						.append(" exception=").append(t.getClass().getName())
//				;
				Object[] objects = buildObjectCommon(stackElement.deltaTime, stackElement.reference);
				totalsent++;
				boolean offered = queue.offer(objects);
				if(!offered) {
					totalerror++;
				}
			}
		}
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
