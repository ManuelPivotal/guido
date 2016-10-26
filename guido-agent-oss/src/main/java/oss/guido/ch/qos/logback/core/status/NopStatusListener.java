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
package oss.guido.ch.qos.logback.core.status;

import oss.guido.ch.qos.logback.core.status.Status;
import oss.guido.ch.qos.logback.core.status.StatusListener;

/**
 * A no-operation (nop) StatusListener
 *
 * @author Ceki G&uuml;c&uuml;
 * @since 1.0.8
 */
public class NopStatusListener implements StatusListener {

  public void addStatusEvent(Status status) {
   // nothing to do
  }
}
