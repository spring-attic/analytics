/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.cloud.stream.app.analytics.common.CounterCommonConfiguration;
import org.springframework.cloud.stream.app.analytics.common.CounterService;
import org.springframework.cloud.stream.app.analytics.common.OnMissingStreamFunctionDefinitionCondition;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.Message;


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


	// Use the spring.cloud.stream.function.definition to override the default function composition.
	@Bean
	@Conditional(OnMissingStreamFunctionDefinitionCondition.class)
	public IntegrationFlow defaultProcessorFlow(Processor processor) {
		return IntegrationFlows
				.from(processor.input())
				.transform(Message.class, message -> this.counterService.count(message))
				.channel(processor.output())
				.get();
	}
}
