/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.ecosystem.io.amqp;

import java.io.IOException;
import java.util.Map;
import javax.jms.JMSContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.pulsar.io.common.IOConfigUtils;
import org.apache.pulsar.io.core.SourceContext;
import org.apache.pulsar.io.core.annotations.FieldDoc;


/**
 * QpidJms source config.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class AmqpSourceConfig extends AmqpBaseConfig {

    // Default session mode
    @FieldDoc(
            defaultValue = "1",
            help = "the session mode."
    )
    private int sessionMode = JMSContext.AUTO_ACKNOWLEDGE;

    public static AmqpSourceConfig load(Map<String, Object> config, SourceContext sourceContext) throws IOException {
        return IOConfigUtils.loadWithSecrets(config,  AmqpSourceConfig.class, sourceContext);
    }

    public int getSessionMode() {
        return sessionMode;
    }
}
