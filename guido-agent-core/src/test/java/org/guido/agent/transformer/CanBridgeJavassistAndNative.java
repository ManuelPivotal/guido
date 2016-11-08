package org.guido.agent.transformer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class CanBridgeJavassistAndNative {
	@Test
	public void canExtractMethodFromOriginal() throws Exception {
		CtClass driver = ClassPool.getDefault().get("org.postgresql.Driver");
		Class<?> original = Class.forName("org.postgresql.Driver");
		
		Map<String, Method> methodSet = buildFrom(original);
		
		for(CtMethod method : driver.getDeclaredMethods()) {
			System.out.println(method.getLongName());
			Assert.assertNotNull((methodSet.get(method.getLongName())));
			methodSet.remove(method.getLongName());
		}
		Assert.assertEquals(0, methodSet.size());
	}
	
	private Map<String, Method> buildFrom(Class<?> original) {
		HashMap<String, Method> map = new HashMap<String, Method>();
		
		for(Method method : original.getDeclaredMethods()) {
			StringBuilder sb = new StringBuilder();
	        sb.append(method.getDeclaringClass().getTypeName()).append('.');
	        sb.append(method.getName());
	        sb.append('(');
            separateWithCommas(method.getParameterTypes(), sb);
            sb.append(')');
            map.put(sb.toString(), method);
		}
		return map;
	}

	void separateWithCommas(Class<?>[] types, StringBuilder sb) {
        for (int j = 0; j < types.length; j++) {
            sb.append(types[j].getTypeName());
            if (j < (types.length - 1))
                sb.append(",");
        }
    }

}
