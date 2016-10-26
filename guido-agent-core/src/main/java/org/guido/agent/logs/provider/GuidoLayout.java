package org.guido.agent.logs.provider;

import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.ch.qos.logback.core.CoreConstants;
import oss.guido.ch.qos.logback.core.LayoutBase;

public class GuidoLayout extends LayoutBase<ILoggingEvent> {

	@Override
	public String doLayout(ILoggingEvent event) {
		StringBuffer sbuf = new StringBuffer(128);
	    sbuf.append(event.getTimeStamp());
	    sbuf.append(" ");
	    sbuf.append(event.getLevel());
	    sbuf.append(" [");
	    sbuf.append(event.getThreadName());
	    sbuf.append("] ");
	    sbuf.append(" - ");
	    sbuf.append(event.getFormattedMessage());
	    sbuf.append(CoreConstants.LINE_SEPARATOR);
	    return sbuf.toString();
	}
}
