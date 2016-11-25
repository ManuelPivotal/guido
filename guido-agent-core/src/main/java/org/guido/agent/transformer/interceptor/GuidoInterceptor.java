package org.guido.agent.transformer.interceptor;

import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_ALLOWED;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_SHORT_SIGNATURE;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_THRESHOLD;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import oss.guido.javassist.CtMethod;

public class GuidoInterceptor {
	
	static public Map<Integer, Object[]> references;
	static public Deque<Object[]> queue;
	public static String pid;
	public static long threshold = 500000; // (0.5 ms)
	static public Map<String, String> extraProps;
	
	public static final int MAX_STACK_DEPTH = 128;
	
	static long totalsent = 0;
	static long totalerror = 0;
	
	public GuidoInterceptor() {
	}

	static ThreadLocal<String> threadUuid = new ThreadLocal<String>();
	static ThreadLocal<InThreadStackElement[]> localRefStack = new ThreadLocal<InThreadStackElement[]>();
	static ThreadLocal<SimpleOpInteger> positionInStack = new ThreadLocal<SimpleOpInteger>();
	
	static public InThreadStackElement[] localRefStack() {
		return localRefStack.get();
	}
	
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
		int currentDepth = positionInStack.get().value();
		if(currentDepth == -1) {
			threadUuid.set(UUID.randomUUID().toString());
		}
		if(currentDepth < MAX_STACK_DEPTH) {
			localRefStack.get()[positionInStack.get().addAndGet()].start(index, references.get(index));
		}
	}
	
	static public void pop() {
		SimpleOpInteger posInStack = positionInStack.get();
		if(posInStack.value() < MAX_STACK_DEPTH) {
			InThreadStackElement stackElement = localRefStack.get()[posInStack.getAndDec()].stop();
			if(passes(stackElement)) {
				Object[] objects = buildObjectCommon(stackElement);
				totalsent++;
				boolean offered = queue.offer(objects);
				if(!offered) {
					totalerror++;
				}
			}
			int depth = posInStack.value();
			if(depth >= 0) {
				localRefStack.get()[depth].addCallee(stackElement.refIndex, 
												stackElement.duration,
												(String)stackElement.reference[REF_SHORT_SIGNATURE]);
			}
		}
	}

	static public void popInError(Throwable t) {
		pop();
	}

	static private boolean passes(InThreadStackElement stackElement) {
		return stackElement.duration > (long)stackElement.reference[REF_THRESHOLD] && (Boolean)stackElement.reference[REF_ALLOWED];
	}
	
	static private Object[] buildObjectCommon(InThreadStackElement stackElement) {
		Object[] logObjects = new Object[6 + 1];
		int depth = positionInStack.get().value() + 1;
		int index = 0;
		logObjects[index++] = stackElement.reference[REF_SHORT_SIGNATURE];
		logObjects[index++] = stackElement.duration;
		logObjects[index++] = depth;
		logObjects[index++] = (depth > 0) ? localRefStack.get()[depth - 1].reference[REF_SHORT_SIGNATURE]: "n/a";
		logObjects[index++] = threadUuid.get();
		logObjects[6] = stackElement.topCalleeElements();
		logObjects[5] = stackElement.unaccountedDuration();
		return logObjects;
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
