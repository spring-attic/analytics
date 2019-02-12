package org.springframework.cloud.stream.app.analytics.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.stream.app.analytics.common.CounterCommonProperties.MESSAGE_TAG;

/**
 * @author Christian Tzolov
 */
@Component
public class DefaultCounterService implements CounterService {

	private static final Log logger = LogFactory.getLog(DefaultCounterService.class);

	private CounterCommonProperties properties;

	private ObjectMapper mapper;

	private MeterRegistry[] meterRegistries;

	private BeanFactory beanFactory;

	private EvaluationContext context;

	@PostConstruct
	public void init() {

		this.context = ExpressionUtils.createStandardEvaluationContext(this.beanFactory);
		logger.info("Counter Properties:  " + properties.toString());
	}

	public DefaultCounterService(CounterCommonProperties properties, ObjectMapper mapper, MeterRegistry[] meterRegistries, BeanFactory beanFactory) {
		this.properties = properties;
		this.mapper = mapper;
		this.meterRegistries = meterRegistries;
		this.beanFactory = beanFactory;
	}

	@Override
	public Message<?> count(Message<?> message) {

		String counterName = computeCounterName(message);

		// All fixed tags together are passed with every counter increment.
		Tags fixedTags = this.toTags(this.properties.getTag().getFixed());

		// Message Counter
		this.increment("message." + counterName, Tags.of(fixedTags).and(MESSAGE_TAG));

		Map<String, List<Tag>> allGroupedTags = new HashMap<>();
		// Tag Expressions Counter
		if (this.properties.getTag().getExpression() != null) {

			Map<String, List<Tag>> groupedTags = this.properties.getTag().getExpression().entrySet().stream()
					// maps a <name, expr> pair into [<name, expr#val_1>, ... <name, expr#val_N>] Tag array.
					.map(namedExpression ->
							toList(namedExpression.getValue().getValue(this.context, message)).stream()
									.map(tagValue -> Tag.of(namedExpression.getKey(), tagValue))
									.collect(Collectors.toList())).flatMap(List::stream)
					.collect(Collectors.groupingBy(tag -> tag.getKey(), Collectors.toList()));

			allGroupedTags.putAll(groupedTags);
			//this.count(counterName, fixedTags, groupedTags);
		}

		// Field Name/Value Tags Counter
		if (!CollectionUtils.isEmpty(this.properties.getTag().getFields())) {

			Map<String, List<Tag>> groupedTags = this.properties.getTag().getFields().stream()
					.map(fieldName -> this.computeFieldValues(fieldName, message).stream()
							.map(fieldValue -> Tag.of(fieldName, fieldValue))
							.collect(Collectors.toList())).flatMap(Collection::stream)
					.collect(Collectors.groupingBy(tag -> tag.getKey(), Collectors.toList()));

			allGroupedTags.putAll(groupedTags);
		}

		this.count(counterName, fixedTags, allGroupedTags);

		return message;
	}

	private void count(String counterName, Tags fixedTags, Map<String, List<Tag>> groupedTags) {
		if (!CollectionUtils.isEmpty(groupedTags)) {
			int max = groupedTags.values().stream().map(l -> l.size()).max(Integer::compareTo).get();
			for (int i = 0; i < max; i++) {
				Tags currentTags = Tags.of(fixedTags);
				for (Map.Entry<String, List<Tag>> e : groupedTags.entrySet()) {
					currentTags = (e.getValue().size() > i) ?
							currentTags.and(e.getValue().get(i)) :
							currentTags.and(Tags.of(e.getKey(), ""));
				}
				this.increment(counterName, currentTags);
			}
		}
	}

	protected String computeCounterName(Message<?> message) {
		return this.properties.getComputedNameExpression()
				.getValue(this.context, message, CharSequence.class).toString();
	}

	protected List<String> computeFieldValues(String fieldName, Message<?> message) {

		Object payload = message.getPayload();

		if (payload instanceof byte[]) {
			try {
				payload = this.mapper.readValue((byte[]) payload, Map.class);
			}
			catch (Exception e) {
				throw new RuntimeException("Not JSON content!", e);
			}
		}

		Object value = null;
		if (payload instanceof Map) {
			Map map = (Map) payload;
			if (map.containsKey(fieldName)) {
				value = map.get(fieldName);
			}
			else {
				logger.error("The property '" + fieldName + "' is not available in the payload: " + payload);
			}
		}
		else {
			BeanWrapper beanWrapper = new BeanWrapperImpl(payload);
			if (beanWrapper.isReadableProperty(fieldName)) {
				value = beanWrapper.getPropertyValue(fieldName);
			}
			else {
				logger.error("The property '" + fieldName + "' is not available in the payload: " + payload);
			}
		}

		if (value == null) {
			logger.info("The value for the property '" + fieldName + "' in the payload '" + payload + "' is null. Ignored");
		}

		return toList(value);
	}

	/**
	 * Converts a key/value Map into Tag(key,value) list. Filters out the empty key/value pairs.
	 * @param keyValueMap key/value map to convert into tags.
	 * @return Returns Tags list representing every non-empty key/value pair.
	 */
	protected Tags toTags(Map<String, String> keyValueMap) {
		return CollectionUtils.isEmpty(keyValueMap) ? Tags.empty() :
				Tags.of(keyValueMap.entrySet().stream()
						.filter(e -> StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue()))
						.map(e -> Tag.of(e.getKey(), e.getValue()))
						.collect(Collectors.toList()));
	}

	/**
	 * Converts the input value into an list of values. If the value is not a collection/array type the result
	 * is a single element list. For collection/array input value the result is the list of stringifie content of
	 * this collection.
	 * @param value input value can be array, collection or single value.
	 * @return Returns value list.
	 */
	protected List<String> toList(Object value) {
		if (value != null) {
			if ((value instanceof Collection) || ObjectUtils.isArray(value)) {
				Collection<?> valueCollection = (value instanceof Collection) ? (Collection<?>) value
						: Arrays.asList(ObjectUtils.toObjectArray(value));

				return valueCollection.stream()
						.map(Object::toString)
						.filter(StringUtils::hasText)
						.collect(Collectors.toList());
			}
			else {
				return Arrays.asList(value.toString());
			}
		}
		return Collections.emptyList();
	}

	private void count(String counterName, Tags commonTags, List<Tag> exclusiveTags) {
		logger.info("Counter:" + counterName + ", commonTags: " + commonTags + ", exclusive tags:" + exclusiveTags);
		exclusiveTags.stream()
				.filter(tag -> StringUtils.hasText(tag.getKey()) && StringUtils.hasText(tag.getValue()))
				.forEach(tag -> this.increment(counterName, Tags.of(commonTags).and(tag)));
	}

	/**
	 * Increment the counterName increment for every configured MaterRegistry.
	 * @param counterName The increment to increment.
	 * @param tags List of tags (e.g. dimensions) associated with this increment increment.
	 */
	protected void increment(String counterName, Iterable<Tag> tags) {
		for (MeterRegistry meterRegistry : this.meterRegistries) {
			meterRegistry.counter(counterName, tags).increment();
		}
	}

}
