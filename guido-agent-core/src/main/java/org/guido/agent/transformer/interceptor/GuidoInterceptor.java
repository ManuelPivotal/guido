package org.guido.agent.transformer.interceptor;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.guido.agent.transformer.PerClassConfig;
import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.javassist.CtMethod;

public class GuidoInterceptor {
	
	static public List<Map<String, Object>> references;
	static public Deque<String> queue;
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
		GuidoInterceptor.references = (List<Map<String, Object>>)paramMap.get("refs");
		GuidoInterceptor.queue = (Deque)paramMap.get("logQ");
		GuidoInterceptor.pid = (String)paramMap.get("pid");
		GuidoInterceptor.threshold = (long)paramMap.get("threshold");
		GuidoInterceptor.extraProps = (Map<String, String>)paramMap.get("extraprops");
	}
	
	static public Class<?>[] toLoad = new Class<?>[] {
		InThreadStackElement.class,
		SimpleOpInteger.class,
		GuidoLogger.class,
		PerClassConfig.class
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
			localRefStack.get()[positionInStack.get().addAndGet()].start(references.get(index));
		}
	}
	
	static public void pop(Object[] args) {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(stackElement.deltaTime > (long)stackElement.reference.get("threshold") 
					&& (Boolean)stackElement.reference.get("allowed")) {
				StringBuffer sb = new StringBuffer(2048)
					.append(stackElement.reference.get("className"))
					.append(" - pid=").append(pid)
					.append(" threadUuid=").append(threadUuid.get())
					.append(" depth=").append(positionInStack.get().get() + 1)
					.append(" methodCalled=").append(stackElement.reference.get("shortSignature"))
					.append(" durationInNS=").append(stackElement.deltaTime)
				;
				totalsent++;
				boolean offered = queue.offer(sb.toString());
				if(!offered) {
					totalerror++;
				}
			}
		}
	}

	static public void popInError(Throwable t) {
		if(positionInStack.get().get() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
			if(stackElement.deltaTime > (long)stackElement.reference.get("threshold") 
					&& (Boolean)stackElement.reference.get("allowed")) {
				StringBuffer sb = new StringBuffer(2048)
					.append(stackElement.reference.get("className"))
					.append(" - pid=").append(pid)
					.append(" threadUuid=").append(threadUuid.get())
					.append(" depth=").append(positionInStack.get().get() + 1)
					.append(" methodCalled=").append(stackElement.reference.get("shortSignature"))
					.append(" durationInNS=").append(stackElement.deltaTime)
					.append(" exception=").append(t.getClass().getName())
				;
				totalsent++;
				boolean offered = queue.offer(sb.toString());
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
