package org.guido.agent.transformer.interceptor;

import static java.util.UUID.randomUUID;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.javassist.CtMethod;
import oss.guido.javassist.bytecode.Descriptor;

public class GuidoInterceptor {
	
	static public List<Map<String, String>> references = new ArrayList<Map<String, String>>(32 * 1024);
	static public Deque<String> queue;
	
	public GuidoInterceptor() {
		System.out.println("GuidoInterceptor empty created by " + getClass().getClassLoader());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GuidoInterceptor(Object mapRef) {
		System.out.println("GuidoInterceptor created by " + getClass().getClassLoader());
		Map<String, Object> paramMap = (Map<String, Object>)mapRef;
		GuidoInterceptor.references = (List<Map<String, String>>)paramMap.get("refs");
		GuidoInterceptor.queue = (Deque)paramMap.get("logQ");
		GuidoInterceptor.pid = (String)paramMap.get("pid");
	}
	
	static public Class<?>[] toLoad = new Class<?>[] {
		InThreadStackElement.class,
		SimpleOpInteger.class,
		GuidoLogger.class
	};
	
	static ThreadLocal<String> threadUuid = new ThreadLocal<String>();
	static ThreadLocal<InThreadStackElement[]> localRefStack = new ThreadLocal<InThreadStackElement[]>();
	static ThreadLocal<SimpleOpInteger> positionInStack = new ThreadLocal<SimpleOpInteger>();
	public static String pid;
	
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
		ref.put("className", method.getDeclaringClass().getName());
		ref.put("longName", method.getLongName());
		ref.put("name", method.getName());
		ref.put("shortSignature", method.getDeclaringClass().getName() + "." + method.getName());
		ref.put("signature", Descriptor.toString(method.getSignature()));
		return ref;
	}

	static public void push(int index) {
		InThreadStackElement[] stack = localRefStack.get();
		if(stack == null) {
			initTLSElements();
		}
		localRefStack.get()[positionInStack.get().addAndGet()].start(references.get(index));
//		String depth = buildDepth(positionInStack.get().value);
//		GuidoLogger.noop(depth + " push(" + index + ") > " + references.get(index).get("longName"));
	}
	
	static public void pop() {
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()].stop();
		if(stackElement.deltaTime / 1000 > 500) {
			String message = String.format("pid=%s threadUuid=%s depth=%d methodCalled=%s durationInNS=%d",
					pid,
					threadUuid.get(),
					positionInStack.get().get() + 1,
					//stackElement.reference.get("className"),
					stackElement.reference.get("shortSignature"),
					stackElement.deltaTime
					);
			boolean offered = queue.offer(message);
//			String depth = buildDepth(positionInStack.get().get() + 1);
//			GuidoLogger.noop(depth 
//				+ " pop() > " + offered + " "
//				+ stackElement.reference.get("longName")
//				+ " - " + stackElement.deltaTime / 1000 + " ms");
		}
	}

	static public void popInError(Throwable t) {
		InThreadStackElement stackElement = localRefStack.get()[positionInStack.get().getAndDec()];
		String message = String.format("exception=%s pid=%s threadUuid=%s depth=%d methodCalled=%s durationInNS=%d",
				t.getClass().getName(),
				pid,
				threadUuid.get(),
				positionInStack.get().get() + 1,
				stackElement.reference.get("shortSignature"),
				stackElement.deltaTime
				);
		boolean offered = queue.offer(message);
//		if(stackElement.deltaTime / 1000 > 500) {
//			String depth = buildDepth(positionInStack.get().get() + 1);
//			GuidoLogger.noop(depth + " !!!! popInError(" + t.getClass() + ") > " + stackElement.reference.get("longName"));
//		}
	}
	
	private static void initTLSElements() {
		InThreadStackElement[] stack = new InThreadStackElement[2048];
		for(int index = 0; index < stack.length; index++) {
			stack[index] = new InThreadStackElement(randomUUID().toString());
		}
		threadUuid.set(UUID.randomUUID().toString());
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
