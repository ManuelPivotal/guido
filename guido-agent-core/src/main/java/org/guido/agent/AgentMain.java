package org.guido.agent;

import java.lang.instrument.Instrumentation;

import org.guido.agent.transformer.GuidoTransformer;
import org.guido.agent.transformer.logger.GuidoLogger;

public class AgentMain {
	  public static void premain(String args, Instrumentation instrumentation) {
		  GuidoLogger.debug("Guido agent starting ...");
		  GuidoTransformer transformer = new GuidoTransformer();
		  instrumentation.addTransformer(transformer);
	  }
}
