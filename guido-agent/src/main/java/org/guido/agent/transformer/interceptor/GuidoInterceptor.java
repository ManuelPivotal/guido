package org.guido.agent.transformer.interceptor;

import java.util.ArrayList;
import java.util.List;

import javassist.CtMethod;

import org.guido.agent.transformer.logger.GuidoLogger;

public class GuidoInterceptor {
	
	static class MethodReference {
		String className;
		CtMethod method;
		public MethodReference(String className, CtMethod method) {
			this.className = className;
			this.method = method;
		}
	}
	
	static class SimpleOpInteger {
		int value = -1;
		
		int get() {
			return value;
		}

		int addAndGet() {
			value++;
			return value;
		}
		int getAndDec() {
			value--;
			return value + 1;
		}
	}
	
	static class InThreadStackElement {
		MethodReference reference;
		long nanoTime;

		public void initWith(MethodReference reference) {
			this.reference = reference;
			this.nanoTime = System.nanoTime();
		}
	}
	
	static ThreadLocal<InThreadStackElement[]> localRefStack = new ThreadLocal<InThreadStackElement[]>();
	static ThreadLocal<SimpleOpInteger> positionInStack = new ThreadLocal<SimpleOpInteger>();
	
	static List<MethodReference> references = new ArrayList<MethodReference>(32 * 1024);
	
	static public String insertBefore(String className, CtMethod method) {
		int referenceNumber = 0;
		synchronized(references) {
			references.add(new MethodReference(className, method));
			referenceNumber = references.size() - 1;
		}
		GuidoLogger.debug("creating push(" + referenceNumber + ")");
		return new StringBuffer()
			.append("{")
			.append("org.guido.agent.transformer.interceptor.GuidoInterceptor.push(")
			.append(referenceNumber)
			.append(");")
			.append("}")
			.toString();
	}

	static public String insertAfter() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.pop(); }";
	}
	
	public static String addCatch() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.popInError($e); throw $e; }";
	}
	
	static public void push(int index) {
		InThreadStackElement[] stack = localRefStack.get();
		if(stack == null) {
			initStackElement();
		}
		localRefStack.get()[positionInStack.get().addAndGet()].initWith(references.get(index));
		String depth = buildDepth(positionInStack.get().value);
		GuidoLogger.info(depth + " push(" + index + ")" + references.get(index).method.getName());
	}

	static public void pop() {
		String depth = buildDepth(positionInStack.get().get());
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()];
		GuidoLogger.info(depth + " pop() from " + stackElement.reference.method.getName());
	}

	private static String buildDepth(int value) {
		StringBuffer sb = new StringBuffer();
		for(int index = 0; index <= (value + 1); index++) {
			sb.append("|--");
		}
		return sb.toString();
	}

	static public void popInError(Exception t) {
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()];
		GuidoLogger.info("popInError() from " + stackElement.reference.method.getName());
	}
	
	private static void initStackElement() {
		InThreadStackElement[] stack = new InThreadStackElement[2048];
		for(int index = 0; index < stack.length; index++) {
			stack[index] = new InThreadStackElement();
		}
		localRefStack.set(stack);
		positionInStack.set(new SimpleOpInteger());
	}
}
