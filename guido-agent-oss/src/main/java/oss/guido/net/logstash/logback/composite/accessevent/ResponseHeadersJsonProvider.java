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
package oss.guido.net.logstash.logback.composite.accessevent;

import java.io.IOException;

import oss.guido.ch.qos.logback.access.spi.IAccessEvent;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.net.logstash.logback.composite.AbstractFieldJsonProvider;
import oss.guido.net.logstash.logback.composite.FieldNamesAware;
import oss.guido.net.logstash.logback.composite.JsonWritingUtils;
import oss.guido.net.logstash.logback.fieldnames.LogstashAccessFieldNames;

public class ResponseHeadersJsonProvider extends AbstractFieldJsonProvider<IAccessEvent> implements FieldNamesAware<LogstashAccessFieldNames> {

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
        JsonWritingUtils.writeMapStringFields(generator, getFieldName(), event.getResponseHeaderMap());
    }
    
    @Override
    public void setFieldNames(LogstashAccessFieldNames fieldNames) {
        setFieldName(fieldNames.getFieldsResponseHeaders());
    }

}
