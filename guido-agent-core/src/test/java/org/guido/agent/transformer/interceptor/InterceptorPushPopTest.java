package org.guido.agent.transformer.interceptor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import org.guido.agent.transformer.GuidoTransformer;
import org.junit.Test;

public class InterceptorPushPopTest {
	
	@Test
	public void canStands20plusCallees() throws Exception {
		int index;
		Map<Integer, Object[]> references = new HashMap<Integer, Object[]>();
		for(index = 1; index < 100; index++) {
			references.put(index, GuidoTransformer.newMethodReference("a.a." + index));
		}

		GuidoInterceptor.references = references;
		GuidoInterceptor.queue = new LinkedBlockingDeque<Object[]>(8192);
		GuidoInterceptor.threshold = 0;
		GuidoInterceptor.push(1);
		for(index = 2; index < 100; index++) {
			GuidoInterceptor.push(index);
			Thread.sleep(index + 10);
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
		}
		
		while(!GuidoInterceptor.queue.isEmpty()) {
			Object[] logContents = GuidoInterceptor.queue.poll();
			Object[] elements = (Object[])logContents[6];
			if(elements.length == 0) {
				continue;
			}
			Double duration = toMicroSec(logContents[1]);
			Double nonInstrDuration = toMicroSec(logContents[5]);
			System.out.println(" ---------------------------- ");
			System.out.println(String.format("Method %s", logContents[3]));
			System.out.println(String.format("Total duration is %f", duration));
			System.out.println(String.format("Non instr duration is %f", nonInstrDuration));
			System.out.println(String.format("diff is %f", duration - nonInstrDuration));

			double total = 0.0;
			long totalLong = 0;
			System.out.println("Callees : " + elements.length);
			for(index = 0; index < elements.length; index++) {
				CalleeElement calleeElement = (CalleeElement) elements[index];
				//System.out.println(calleeElement.toString());
				total += calleeElement.getTotalCalls() * calleeElement.getAvg();
				totalLong += calleeElement.totalDuration;
			}
			double dblTotal = (double)totalLong/1000.00;
			System.out.println(String.format("Total callees %f - %d", total, totalLong));
			System.out.println(String.format("Duration %f nonInstrDuration %f totalCallees %f", 
					duration, nonInstrDuration, (double)totalLong/1000.00));
			assertEquals(dblTotal, duration - nonInstrDuration);
		}
	}
	
	@Test
	public void canStackupCallees() throws Exception {
		int index;
		Map<Integer, Object[]> references = new HashMap<Integer, Object[]>();
		
		references.put(1, GuidoTransformer.newMethodReference("a.a"));
		references.put(2, GuidoTransformer.newMethodReference("b.b"));
		references.put(3, GuidoTransformer.newMethodReference("d.d"));
		references.put(4, GuidoTransformer.newMethodReference("e.e"));
		
		GuidoInterceptor.references = references;
		GuidoInterceptor.queue = new LinkedBlockingDeque<Object[]>(8192);
		GuidoInterceptor.threshold = 50;
		
		
		GuidoInterceptor.push(1);
		GuidoInterceptor.push(2);
		for(index = 0; index < 10; index++) {
			GuidoInterceptor.push(3);
			Thread.sleep(5);
			GuidoInterceptor.pop();
			GuidoInterceptor.push(4);
			Thread.sleep(10);
			GuidoInterceptor.pop();
			GuidoInterceptor.push(3);
			Thread.sleep(10);
			GuidoInterceptor.pop();
		}
		//Thread.sleep(1000);
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
		
		assertEquals(20, refstack2.calleeElements[0].totalCalls);
		assertEquals(3, refstack2.calleeElements[0].refIndex); // spend more time in 4
		assertEquals("d.d", refstack2.calleeElements[0].methodCalled);
		
		assertEquals(10, refstack2.calleeElements[1].totalCalls);
		assertEquals(4, refstack2.calleeElements[1].refIndex);
		assertEquals("e.e", refstack2.calleeElements[1].methodCalled);
		
		while(!GuidoInterceptor.queue.isEmpty()) {
			Object[] logContents = GuidoInterceptor.queue.poll();
			Object[] elements = (Object[])logContents[6];
			if(elements.length == 0) {
				continue;
			}
			Double duration = toMicroSec(logContents[1]);
			Double nonInstrDuration = toMicroSec(logContents[5]);
			System.out.println(" ---------------------------- ");
			System.out.println(String.format("Method %s", logContents[0]));
			System.out.println(String.format("Total duration is %f", duration));
			System.out.println(String.format("Non instr duration is %f", nonInstrDuration));
			System.out.println(String.format("diff is %f", duration - nonInstrDuration));

			double total = 0.0;
			long totalLong = 0;
			long totalCurrentDuration = 0;
			System.out.println("Callees : " + elements.length);
			assertEquals(16, elements.length);
			for(index = 0; index < elements.length; index++) {
				CalleeElement calleeElement = (CalleeElement) elements[index];
				if(index == 0) {
					totalCurrentDuration = calleeElement.totalDuration;
				} else {
					assertTrue(totalCurrentDuration >= calleeElement.totalDuration);
					assertNotNull(calleeElement.methodCalled);
					totalCurrentDuration = calleeElement.totalDuration;
				}
				System.out.println(calleeElement.toString());
				total += calleeElement.getTotalCalls() * calleeElement.getAvg();
				totalLong += calleeElement.totalDuration;
			}
			System.out.println(String.format("Total callees %f - %d", total, totalLong));
		}
	}
	private Double toMicroSec(Object object) {
		Double duration = (double)(long)object;
		return duration / 1000.00;
	}

}
