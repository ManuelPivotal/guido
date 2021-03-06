/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package oss.guido.net.logstash.logback.encoder;

import oss.guido.ch.qos.logback.access.spi.IAccessEvent;
import oss.guido.net.logstash.logback.LogstashAccessFormatter;
import oss.guido.net.logstash.logback.composite.CompositeJsonFormatter;
import oss.guido.net.logstash.logback.fieldnames.LogstashAccessFieldNames;

public class LogstashAccessEncoder extends AccessEventCompositeJsonEncoder {
    
    @Override
    protected CompositeJsonFormatter<IAccessEvent> createFormatter() {
        return new LogstashAccessFormatter(this);
    }
    
    @Override
    protected LogstashAccessFormatter getFormatter() {
        return (LogstashAccessFormatter) super.getFormatter();
    }
    
    public LogstashAccessFieldNames getFieldNames() {
        return getFormatter().getFieldNames();
    }
    
    public void setFieldNames(LogstashAccessFieldNames fieldNames) {
        getFormatter().setFieldNames(fieldNames);
    }

    public String getTimeZone() {
        return getFormatter().getTimeZone();
    }

    public void setTimeZone(String timeZoneId) {
        getFormatter().setTimeZone(timeZoneId);
    }

}
