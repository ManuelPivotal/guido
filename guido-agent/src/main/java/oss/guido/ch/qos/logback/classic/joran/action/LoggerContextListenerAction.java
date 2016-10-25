/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package oss.guido.ch.qos.logback.classic.joran.action;

import org.xml.sax.Attributes;

import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.classic.spi.LoggerContextListener;
import oss.guido.ch.qos.logback.core.joran.spi.InterpretationContext;
import oss.guido.ch.qos.logback.core.util.OptionHelper;
import oss.guido.ch.qos.logback.core.joran.action.Action;
import oss.guido.ch.qos.logback.core.joran.spi.ActionException;
import oss.guido.ch.qos.logback.core.spi.ContextAware;
import oss.guido.ch.qos.logback.core.spi.LifeCycle;

public class LoggerContextListenerAction extends Action {
  boolean inError = false;
  LoggerContextListener lcl;

  @Override
  public void begin(InterpretationContext ec, String name, Attributes attributes)
          throws ActionException {

    inError = false;

    String className = attributes.getValue(CLASS_ATTRIBUTE);
    if (OptionHelper.isEmpty(className)) {
      addError("Mandatory \"" + CLASS_ATTRIBUTE
              + "\" attribute not set for <loggerContextListener> element");
      inError = true;
      return;
    }

    try {
      lcl = (LoggerContextListener) OptionHelper.instantiateByClassName(
              className, LoggerContextListener.class, context);

      if(lcl instanceof ContextAware) {
        ((ContextAware) lcl).setContext(context);
      }

      ec.pushObject(lcl);
      addInfo("Adding LoggerContextListener of type [" + className
              + "] to the object stack");

    } catch (Exception oops) {
      inError = true;
      addError("Could not create LoggerContextListener of type " + className + "].", oops);
    }
  }

  @Override
  public void end(InterpretationContext ec, String name) throws ActionException {
    if (inError) {
      return;
    }
    Object o = ec.peekObject();

    if (o != lcl) {
      addWarn("The object on the top the of the stack is not the LoggerContextListener pushed earlier.");
    } else {
      if (lcl instanceof LifeCycle) {
        ((LifeCycle) lcl).start();
        addInfo("Starting LoggerContextListener");
      }
      ((LoggerContext) context).addListener(lcl);
      ec.popObject();
    }
  }

}
