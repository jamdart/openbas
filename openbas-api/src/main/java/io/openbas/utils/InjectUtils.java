package io.openbas.utils;

import io.openbas.database.model.Inject;
import io.openbas.utils.pagination.SearchPaginationInput;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class InjectUtils {

    private InjectUtils() {

    }

    public static boolean checkIfRowIsEmpty(Row row) {
        if (row == null) {
            return true;
        }
        if (row.getLastCellNum() <= 0) {
            return true;
        }
        for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            if (cell != null && cell.getCellType() != CellType.BLANK && StringUtils.isNotBlank(cell.toString())) {
                return false;
            }
        }
        return true;
    }

    private static final String INJECT_KILL_CHAIN_PHASES_FILTER = "inject_kill_chain_phases";
    private static final String INJECT_TYPE_FILTER = "inject_type";
    private static final Map<String, PropertyDescriptor> CORRESPONDENCE_MAP = Map.of(
        INJECT_KILL_CHAIN_PHASES_FILTER,
        PropertyDescriptor.builder()
            .jsonPath("injectorContract.attackPatterns.killChainPhases.id")
            .clazz(String[].class)
            .build(),
        INJECT_TYPE_FILTER,
        PropertyDescriptor.builder()
            .jsonPath("injectorContract.labels")
            .clazz(Map.class)
            .build()
    );

    /**
     * Manage filters that are not directly managed by the generic mechanics -> scenario_kill_chain_phases
     */
    public static Function<Specification<Inject>, Specification<Inject>> handleDeepFilter(
        @NotNull final SearchPaginationInput searchPaginationInput) {
        UnaryOperator<Specification<Inject>> killChainPhasesFilter = CustomFilterUtils.handleDeepFilter(
            searchPaginationInput,
            INJECT_KILL_CHAIN_PHASES_FILTER,
            CORRESPONDENCE_MAP
        );
        UnaryOperator<Specification<Inject>> injectTypeFilter = CustomFilterUtils.handleDeepFilter(
            searchPaginationInput,
            INJECT_TYPE_FILTER,
            CORRESPONDENCE_MAP
        );
        return killChainPhasesFilter.andThen(injectTypeFilter);
    }
}
