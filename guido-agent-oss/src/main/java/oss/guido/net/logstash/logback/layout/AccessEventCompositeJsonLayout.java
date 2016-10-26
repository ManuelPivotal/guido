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
package oss.guido.net.logstash.logback.layout;

import oss.guido.net.logstash.logback.composite.CompositeJsonFormatter;
import oss.guido.net.logstash.logback.composite.JsonProviders;
import oss.guido.net.logstash.logback.composite.accessevent.AccessEventCompositeJsonFormatter;
import oss.guido.net.logstash.logback.composite.accessevent.AccessEventJsonProviders;
import oss.guido.ch.qos.logback.access.spi.IAccessEvent;
import oss.guido.ch.qos.logback.core.joran.spi.DefaultClass;

public class AccessEventCompositeJsonLayout extends CompositeJsonLayout<IAccessEvent> {
    
    
    @Override
    protected CompositeJsonFormatter<IAccessEvent> createFormatter() {
        return new AccessEventCompositeJsonFormatter(this);
    }
    
    @Override
    @DefaultClass(AccessEventJsonProviders.class)
    public void setProviders(JsonProviders<IAccessEvent> jsonProviders) {
        super.setProviders(jsonProviders);
    }
    
}
