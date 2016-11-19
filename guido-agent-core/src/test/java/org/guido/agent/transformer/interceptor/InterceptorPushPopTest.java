package org.guido.agent.transformer.interceptor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import org.guido.agent.transformer.GuidoTransformer;
import org.guido.agent.transformer.interceptor.CalleeElement;
import org.guido.agent.transformer.interceptor.GuidoInterceptor;
import org.guido.agent.transformer.interceptor.InThreadStackElement;
import org.junit.Test;

public class InterceptorPushPopTest {
	
	@Test
	public void canStands20plusCallees() {
		int index;
		Map<Integer, Object[]> references = new HashMap<Integer, Object[]>();
		for(index = 0; index < 50; index++) {
			references.put(index, GuidoTransformer.newMethodReference("a.a." + index));
		}

		GuidoInterceptor.references = references;
		GuidoInterceptor.queue = new LinkedBlockingDeque<Object[]>(8192);
		GuidoInterceptor.push(0);
		for(index = 0; index < 50; index++) {
			GuidoInterceptor.push(index);
			GuidoInterceptor.pop();
		}
		GuidoInterceptor.pop();
		
		InThreadStackElement[] localRefStack = GuidoInterceptor.localRefStack();
		assertNotNull(localRefStack);
		
		InThreadStackElement refstack0 = localRefStack[0];
		assertEquals(InThreadStackElement.getMaxCallee(), refstack0.totalCallees);

		CalleeElement[] callees = refstack0.calleeElements;
		
		for(index = 0; index < InThreadStackElement.getMaxCallee(); index++) {
			assertEquals(1, callees[index].totalCalls);
			assertEquals(index, callees[index].refIndex);
		}
	}
	
	@Test
	public void canStackupCallees() {
		int index;
		Map<Integer, Object[]> references = new HashMap<Integer, Object[]>();
		
		references.put(1, GuidoTransformer.newMethodReference("a.a"));
		references.put(2, GuidoTransformer.newMethodReference("b.b"));
		references.put(3, GuidoTransformer.newMethodReference("d.d"));
		references.put(4, GuidoTransformer.newMethodReference("e.e"));
		
		GuidoInterceptor.references = references;
		GuidoInterceptor.queue = new LinkedBlockingDeque<Object[]>(8192);
		
		GuidoInterceptor.push(1);
		GuidoInterceptor.push(2);
		for(index = 0; index < 10; index++) {
			GuidoInterceptor.push(3);
			GuidoInterceptor.pop();
			GuidoInterceptor.push(4);
			GuidoInterceptor.pop();
		}
		GuidoInterceptor.pop();
		GuidoInterceptor.pop();
		
		InThreadStackElement[] localRefStack = GuidoInterceptor.localRefStack();
		assertNotNull(localRefStack);
		
		InThreadStackElement refstack1 = localRefStack[0];
		assertEquals(1, refstack1.totalCallees);
		assertEquals(1, refstack1.calleeElements[0].totalCalls);
		assertEquals(2, refstack1.calleeElements[0].refIndex);
		assertEquals("b.b", refstack1.calleeElements[0].methodCalled);
		
		InThreadStackElement refstack2 = localRefStack[1];
		assertEquals(2, refstack2.totalCallees);
		
		assertEquals(10, refstack2.calleeElements[0].totalCalls);
		assertEquals(3, refstack2.calleeElements[0].refIndex);
		assertEquals("d.d", refstack2.calleeElements[0].methodCalled);
		
		assertEquals(10, refstack2.calleeElements[1].totalCalls);
		assertEquals(4, refstack2.calleeElements[1].refIndex);
		assertEquals("e.e", refstack2.calleeElements[1].methodCalled);
	}
}
