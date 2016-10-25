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
package oss.guido.ch.qos.logback.core.pattern.color;

import oss.guido.ch.qos.logback.core.pattern.color.ANSIConstants;
import oss.guido.ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * Encloses a given set of converter output in green using the appropriate ANSI escape codes.
 * @param <E>
 * @author  Ceki G&uuml;lc&uuml;
 * @since 1.0.5
 */
public class GreenCompositeConverter<E> extends ForegroundCompositeConverterBase<E> {

  @Override
  protected String getForegroundColorCode(E event) {
    return ANSIConstants.GREEN_FG;
  }
}
