/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.metrics.export.prometheus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;

/**
 * {@link Endpoint @Endpoint} that outputs metrics in a format that can be scraped by the
 * Prometheus server.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Moritz Halbritter
 * @since 2.0.0
 */
@WebEndpoint(id = "prometheus")
public class PrometheusScrapeEndpoint {

	private static final int METRICS_SCRAPE_CHARS_EXTRA = 1024;

	private final PrometheusRegistry prometheusRegistry;

	private final ExpositionFormats expositionFormats;

	private volatile int nextMetricsScrapeSize = 16;

	/**
	 * Creates a new {@link PrometheusScrapeEndpoint}.
	 * @param prometheusRegistry the Prometheus registry to use
	 * @deprecated since 3.3.1 for removal in 3.5.0 in favor of
	 * {@link #PrometheusScrapeEndpoint(PrometheusRegistry, Properties)}
	 */
	@Deprecated(since = "3.3.1", forRemoval = true)
	public PrometheusScrapeEndpoint(PrometheusRegistry prometheusRegistry) {
		this(prometheusRegistry, null);
	}

	/**
	 * Creates a new {@link PrometheusScrapeEndpoint}.
	 * @param prometheusRegistry the Prometheus registry to use
	 * @param expositionFormatsProperties the properties used to configure Prometheus'
	 * {@link ExpositionFormats}
	 */
	public PrometheusScrapeEndpoint(PrometheusRegistry prometheusRegistry, Properties expositionFormatsProperties) {
		this.prometheusRegistry = prometheusRegistry;
		PrometheusProperties prometheusProperties = (expositionFormatsProperties != null)
				? PrometheusPropertiesLoader.load(expositionFormatsProperties) : PrometheusPropertiesLoader.load();
		this.expositionFormats = ExpositionFormats.init(prometheusProperties.getExporterProperties());
	}

	@ReadOperation(producesFrom = PrometheusOutputFormat.class)
	public WebEndpointResponse<byte[]> scrape(PrometheusOutputFormat format, @Nullable Set<String> includedNames) {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(this.nextMetricsScrapeSize);
			MetricSnapshots metricSnapshots = (includedNames != null)
					? this.prometheusRegistry.scrape(includedNames::contains) : this.prometheusRegistry.scrape();
			format.write(this.expositionFormats, outputStream, metricSnapshots);
			byte[] content = outputStream.toByteArray();
			this.nextMetricsScrapeSize = content.length + METRICS_SCRAPE_CHARS_EXTRA;
			return new WebEndpointResponse<>(content, format);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Writing metrics failed", ex);
		}
	}

}
