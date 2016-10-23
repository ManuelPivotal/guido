package org.guido.agent.transformer.interceptor;

import static java.util.UUID.randomUUID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CtMethod;
import javassist.bytecode.Descriptor;

import org.guido.agent.transformer.logger.GuidoLogger;

public class GuidoInterceptor {
	
	public GuidoInterceptor() {
		System.out.println("GuidoInterceptor created by " + getClass().getClassLoader());
	}

	@SuppressWarnings("unchecked")
	public GuidoInterceptor(Object ref) {
		System.out.println("GuidoInterceptor created by " + getClass().getClassLoader());
		if(ref instanceof List) {
			GuidoInterceptor.references = (List<Map<String, String>>)ref;
		}
	}
	
	static public Class<?>[] toLoad = new Class<?>[] {
		InThreadStackElement.class,
		SimpleOpInteger.class,
		GuidoLogger.class
	};
	
	static ThreadLocal<InThreadStackElement[]> localRefStack = new ThreadLocal<InThreadStackElement[]>();
	static ThreadLocal<SimpleOpInteger> positionInStack = new ThreadLocal<SimpleOpInteger>();
	
	static public List<Map<String, String>> references = new ArrayList<Map<String, String>>(32 * 1024);
	
	static public String insertBefore(String className, CtMethod method) {
		return String
				.format(
				"{ org.guido.agent.transformer.interceptor.GuidoInterceptor.push(%d); }",
				createReference(className, method)
				);
	}

	private static int createReference(String className, CtMethod method) {
		int referenceNumber;
		synchronized(references) {
			references.add(newMethodReference(className, method));
			referenceNumber = references.size() - 1;
		}
		return referenceNumber;
	}
	
	static public String insertAfter() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.pop(); }";
	}
	
	public static String addCatch() {
		return "{ org.guido.agent.transformer.interceptor.GuidoInterceptor.popInError($e); throw $e; }";
	}

	public static String insertDebugBefore(String className, CtMethod method) {
		return "{ System.out.println(\" >>> \" + Thread.currentThread().getContextClassLoader()); }";
	}

	private static Map<String, String> newMethodReference(String className, CtMethod method) {
		Map<String, String> ref = new HashMap<String, String>();
		ref.put("className", className);
		ref.put("longName", method.getLongName());
		ref.put("name", method.getName());
		ref.put("signature", Descriptor.toString(method.getSignature()));
		return ref;
	}

	static public void push(int index) {
		InThreadStackElement[] stack = localRefStack.get();
		if(stack == null) {
			initStackElement();
		}
		localRefStack.get()[positionInStack.get().addAndGet()].start(references.get(index));
		String depth = buildDepth(positionInStack.get().value);
		GuidoLogger.noop(depth + " push(" + index + ") > " + references.get(index).get("longName"));
	}
	
	static public void pop() {
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
		if(stackElement.deltaTime / 1000 > 500) {
			String depth = buildDepth(positionInStack.get().get() + 1);
			GuidoLogger.info(depth 
				+ " pop() > " 
				+ stackElement.reference.get("longName")
				+ " - " + stackElement.deltaTime / 1000 + " ms");
		}
	}

	static public void popInError(Throwable t) {
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()];
		if(stackElement.deltaTime / 1000 > 500) {
			String depth = buildDepth(positionInStack.get().get() + 1);
			GuidoLogger.info(depth + " !!!! popInError(" + t.getClass() + ") > " + stackElement.reference.get("longName"));
		}
	}
	
	private static void initStackElement() {
		InThreadStackElement[] stack = new InThreadStackElement[2048];
		for(int index = 0; index < stack.length; index++) {
			stack[index] = new InThreadStackElement(randomUUID().toString());
		}
		localRefStack.set(stack);
		positionInStack.set(new SimpleOpInteger());
	}

	private static String buildDepth(int value) {
		StringBuffer sb = new StringBuffer();
		for(int index = 0; index <= (value + 1); index++) {
			sb.append("|--");
		}
		return sb.toString();
	}
}
