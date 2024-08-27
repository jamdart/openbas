package io.openbas.rest.scenario.utils;

import io.openbas.database.model.Filters;
import io.openbas.database.model.Scenario;
import io.openbas.database.specification.ScenarioSpecification;
import io.openbas.utils.CustomFilterUtils;
import io.openbas.utils.PropertyDescriptor;
import io.openbas.utils.pagination.SearchPaginationInput;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.openbas.utils.CustomFilterUtils.computeMode;
import static java.util.Optional.ofNullable;

public class ScenarioUtils {

  private static final String SCENARIO_KILL_CHAIN_PHASES_FILTER = "scenario_kill_chain_phases";
  private static final String SCENARIO_PLATFORMS_FILTER = "scenario_platforms";
  private static final Map<String, PropertyDescriptor> CORRESPONDENCE_MAP = Map.of(
      SCENARIO_KILL_CHAIN_PHASES_FILTER,
      PropertyDescriptor.builder()
          .jsonPath("injects.injectorContract.attackPatterns.killChainPhases.id")
          .clazz(String.class)
          .build(),
      SCENARIO_PLATFORMS_FILTER,
      PropertyDescriptor.builder()
          .jsonPath("injects.injectorContract.platforms")
          .clazz(String[].class)
          .build()
  );

  private static final String SCENARIO_RECURRENCE_FILTER = "scenario_recurrence";

  private ScenarioUtils() {

  }

  /**
   * Manage filters that are not directly managed by the generic mechanics -> scenario_kill_chain_phases
   */
  public static Function<Specification<Scenario>, Specification<Scenario>> handleDeepFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    UnaryOperator<Specification<Scenario>> killChainPhasesFilter = CustomFilterUtils.handleDeepFilter(
        searchPaginationInput,
        SCENARIO_KILL_CHAIN_PHASES_FILTER,
        CORRESPONDENCE_MAP
    );
    UnaryOperator<Specification<Scenario>> platformsFilter = CustomFilterUtils.handleDeepFilter(
        searchPaginationInput,
        SCENARIO_PLATFORMS_FILTER,
        CORRESPONDENCE_MAP
    );
    return killChainPhasesFilter.andThen(platformsFilter).andThen(handleCustomFilter(searchPaginationInput));
  }

  private static UnaryOperator<Specification<Scenario>> handleCustomFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    // Existence of the filter
    Optional<Filters.Filter> scenarioRecurrenceFilterOpt = ofNullable(searchPaginationInput.getFilterGroup())
        .flatMap(f -> f.findByKey(SCENARIO_RECURRENCE_FILTER));

    if (scenarioRecurrenceFilterOpt.isPresent()) {
      // Purge filter
      searchPaginationInput.getFilterGroup().removeByKey(SCENARIO_RECURRENCE_FILTER);
      Specification<Scenario> customSpecification = null;
      if (scenarioRecurrenceFilterOpt.get().getValues().contains("Scheduled")) {
        customSpecification = ScenarioSpecification.isRecurring();
      } else if (scenarioRecurrenceFilterOpt.get().getValues().contains("Not planned")) {
        customSpecification = ScenarioSpecification.noRecurring();
      }
      // Final specification
      return computeMode(searchPaginationInput, customSpecification);
    } else {
      return (Specification<Scenario> specification) -> specification;
    }
  }

}
