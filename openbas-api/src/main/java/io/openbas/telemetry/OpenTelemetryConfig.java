package io.openbas.telemetry;

import static io.openbas.database.model.SettingKeys.*;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Objects.requireNonNull;

import io.openbas.database.model.Setting;
import io.openbas.database.repository.SettingRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class OpenTelemetryConfig {

  private final Environment env;
  private final SettingRepository settingRepository;
  private final ThreadPoolTaskScheduler taskScheduler;

  @Getter private final Duration collectInterval = Duration.ofMinutes(60);
  @Getter private final Duration exportInterval = Duration.ofMinutes(6 * 60);
  @Autowired private Environment environment;

  private static final DateTimeFormatter CREATION_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.n]");

  @Bean
  public OpenTelemetry openTelemetry() {
    log.info("Start telemetry");
    log.info("Telemetry - Using collect interval: " + collectInterval);
    log.info("Telemetry - Using export interval: " + exportInterval);

    if (!isEndpointReachable(getOTELEndpoint())) {
      return OpenTelemetry.noop();
    }

    Resource resource = buildResource();

    // Set OTLP Exporter
    OtlpHttpMetricExporter otlpExporter =
        OtlpHttpMetricExporter.builder()
            .setEndpoint(getOTELEndpoint())
            .setAggregationTemporalitySelector(instrumentType -> AggregationTemporality.DELTA)
            .build();

    // Set Metric Reader
    MetricReader customMetricReader =
        new CustomMetricReader(otlpExporter, taskScheduler, collectInterval, exportInterval);

    // Create MeterProvider
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(customMetricReader)
            .build();

    return OpenTelemetrySdk.builder().setMeterProvider(meterProvider).buildAndRegisterGlobal();
  }

  @Bean
  public Meter meter(@NotNull final OpenTelemetry openTelemetry) {
    return openTelemetry.getMeter("openbas-api-meter");
  }

  // -- PRIVATE --
  private String getOTELEndpoint() {
    String endpoint = "https://telemetry.obas.filigran.io/v1/metrics";
    if (Arrays.asList(environment.getActiveProfiles()).contains("dev")
        || Arrays.asList(environment.getActiveProfiles()).contains("ci")) {
      endpoint = "https://telemetry.obas.staging.filigran.io/v1/metrics";
    }
    return endpoint;
  }

  private boolean isEndpointReachable(String url) {
    try {
      URL endpoint = new URL(url);
      HttpURLConnection req = (HttpURLConnection) endpoint.openConnection();
      req.setRequestMethod("POST");
      req.setDoOutput(true);
      req.setConnectTimeout(3000);
      req.setReadTimeout(3000);
      req.setRequestProperty("Content-Type", "application/json");
      req.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8)); // Send empty JSON body

      int responseCode = req.getResponseCode();

      if (responseCode != 200) {
        log.error(
            "Telemetry - Failed to reach OTLP endpoint: {} with response code: {}",
            url,
            responseCode);
        return false;
      } else {
        log.info("Telemetry - Successfully reached OTLP endpoint: {}", url);
        return true;
      }

    } catch (IOException e) {
      log.error(String.format("Telemetry - Failed to reach OTLP endpoint: %s", url), e);
      return false;
    }
  }

  private Resource buildResource() {
    Setting instanceId =
        this.settingRepository.findByKey(PLATFORM_INSTANCE.key()).orElse(new Setting());
    Setting instanceCreationDate =
        this.settingRepository.findByKey(PLATFORM_INSTANCE_CREATION.key()).orElse(new Setting());
    LocalDateTime creationDate = LocalDateTime.now();
    if (instanceCreationDate.getValue() != null) {
      creationDate = LocalDateTime.parse(instanceCreationDate.getValue(), CREATION_DATE_FORMATTER);
    }
    ResourceBuilder resourceBuilder =
        Resource.getDefault().toBuilder()
            .put(ServiceAttributes.SERVICE_NAME, "openbas-telemetry")
            .put(ServiceAttributes.SERVICE_VERSION, getRequiredProperty("info.app.version"))
            .put(stringKey("service.instance.id"), instanceId.getValue())
            .put(
                stringKey("service.instance.creation"),
                ZonedDateTime.of(creationDate, ZoneId.systemDefault()).toInstant().toString());

    try {
      String hostAddress = InetAddress.getLocalHost().getHostAddress();
      resourceBuilder.putAll(Attributes.of(ServerAttributes.SERVER_ADDRESS, hostAddress));
    } catch (UnknownHostException e) {
      log.error(String.format("Telemetry - Failed to get host address: %s", e.getMessage()), e);
    }

    return resourceBuilder.build();
  }

  private String getRequiredProperty(@NotBlank final String key) {
    return requireNonNull(env.getProperty(key), "Property " + key + " must not be null");
  }
}
