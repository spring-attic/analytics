/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.counter.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.app.analytics.common.CounterCommonConfiguration;
import org.springframework.cloud.stream.app.analytics.common.CounterService;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;


/**
 *
 * @author Christian Tzolov
 */
@Configuration
@EnableBinding(Processor.class)
@EnableConfigurationProperties({ CounterProcessorProperties.class })
@Import(CounterCommonConfiguration.class)
public class CounterProcessorConfiguration {

	private static final Log logger = LogFactory.getLog(CounterProcessorConfiguration.class);

	@Autowired
	private CounterProcessorProperties processorProperties;

	@Autowired
	private CounterService counterService;

	@StreamListener(Processor.INPUT)
	@SendTo(Processor.OUTPUT)
	public Object evaluate(Message<?> input) {
		this.counterService.count(input);
		return input;
	}
}
