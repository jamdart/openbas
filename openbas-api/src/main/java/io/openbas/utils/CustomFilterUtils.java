package io.openbas.utils;

import io.openbas.database.model.Base;
import io.openbas.database.model.Filters;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static io.openbas.utils.FilterUtilsJpa.computeFilterFromSpecificPath;
import static java.util.Optional.ofNullable;

public class CustomFilterUtils {

  private CustomFilterUtils() {

  }

  /**
   * Manage filters that are not directly managed by the generic mechanics
   */
  public static <T extends Base> UnaryOperator<Specification<T>> handleDeepFilter(
      @NotNull final SearchPaginationInput searchPaginationInput,
      @NotBlank final String customFilterKey,
      @NotNull final Map<String, PropertyDescriptor> correspondenceMap) {
    // Existence of the filter
    Optional<Filters.Filter> killChainPhaseFilterOpt = ofNullable(searchPaginationInput.getFilterGroup())
        .flatMap(f -> f.findByKey(customFilterKey));

    if (killChainPhaseFilterOpt.isPresent()) {
      // Purge filter
      searchPaginationInput.getFilterGroup().removeByKey(customFilterKey);
      Specification<T> customSpecification = computeFilterFromSpecificPath(
          killChainPhaseFilterOpt.get(), correspondenceMap.get(customFilterKey)
      );
      // Final specification
      return computeMode(searchPaginationInput, customSpecification);
    } else {
      return (Specification<T> specification) -> specification;
    }
  }

  public static <T extends Base> UnaryOperator<Specification<T>> computeMode(
      @NotNull final SearchPaginationInput searchPaginationInput,
      Specification<T> customSpecification) {
    if (Filters.FilterMode.and.equals(searchPaginationInput.getFilterGroup().getMode())) {
      return customSpecification::and;
    } else if (Filters.FilterMode.or.equals(searchPaginationInput.getFilterGroup().getMode())) {
      return customSpecification::or;
    } else {
      return (Specification<T> specification) -> specification;
    }
  }

}
