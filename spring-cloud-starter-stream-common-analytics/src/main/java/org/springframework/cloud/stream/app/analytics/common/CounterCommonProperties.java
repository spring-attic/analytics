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

package org.springframework.cloud.stream.app.analytics.common;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.AssertTrue;

import io.micrometer.core.instrument.Tag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.validation.annotation.Validated;


/**
 * @author Christian Tzolov
 */
@ConfigurationProperties("counter")
@Validated
public class CounterCommonProperties {

	public static final String COUNTER_TYPE = "counterType";
	public static final String COMPUTED_COUNTER_TYPE = "computed";
	public static final String MESSAGE_COUNTER_TYPE = "message";
	public static final Tag MESSAGE_TAG = Tag.of(COUNTER_TYPE, MESSAGE_COUNTER_TYPE);

	/**
	 * The default name of the increment
	 */
	@Value("${spring.application.name:counts}")
	private String defaultName;

	/**
	 * The name of the counter to increment.
	 */
	private String name;

	/**
	 * A SpEL expression (against the incoming Message) to derive the name of the counter to increment.
	 */
	private Expression nameExpression;

	/**
	 * Fixed and computed tags to be assignee with the counter increment measurement.
	 */
	private MetricsTag tag = new MetricsTag();

	public static class MetricsTag {

		/**
		 * Custom tags assigned to every counter increment measurements.
		 * This is a map so the property convention fixed tags is: counter.tag.fixed.[tag-name]=[tag-value]
		 */
		private Map<String, String> fixed;

		/**
		 * Computes tags from SpEL expression.
		 * Single SpEL expression can produce an array of values, which in turn means distinct name/value tags.
		 * Every name/value tag will produce a separate counter increment.
		 * Tag expression format is: counter.tag.expression.[tag-name]=[SpEL expression]
		 */
		private Map<String, Expression> expression;

		/**
		 * The field names to extract tag values for the counter increment.
		 * Single field can produce an array of values, which in turn means distinct name/value tags.
		 * Every name/value tag causes a separate counter increment.
		 */
		private List<String> fields;

		public Map<String, String> getFixed() {
			return fixed;
		}

		public void setFixed(Map<String, String> fixed) {
			this.fixed = fixed;
		}

		public Map<String, Expression> getExpression() {
			return expression;
		}

		public void setExpression(Map<String, Expression> expression) {
			this.expression = expression;
		}

		public List<String> getFields() {
			return fields;
		}

		public void setFields(List<String> fields) {
			this.fields = fields;
		}

		@Override
		public String toString() {
			return "MetricsTag{" +
					"fixed=" + fixed +
					", expression=" + expression +
					", fields=" + fields +
					'}';
		}
	}

	public MetricsTag getTag() {
		return tag;
	}

	public String getName() {
		if (name == null && nameExpression == null) {
			return defaultName;
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Expression getNameExpression() {
		return nameExpression;
	}

	public void setNameExpression(Expression nameExpression) {
		this.nameExpression = nameExpression;
	}

	public Expression getComputedNameExpression() {
		return (nameExpression != null ? nameExpression : new LiteralExpression(getName()));
	}

	@AssertTrue(message = "exactly one of 'name' and 'nameExpression' must be set")
	public boolean isExclusiveOptions() {
		return getName() != null ^ getNameExpression() != null;
	}

	@Override
	public String toString() {
		return "CounterCommonProperties{" +
				"defaultName='" + defaultName + '\'' +
				", name=" + name +
				", tag=" + tag +
				'}';
	}
}
