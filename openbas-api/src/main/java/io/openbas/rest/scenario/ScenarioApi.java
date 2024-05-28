package io.openbas.rest.scenario;

import io.openbas.database.model.Scenario;
import io.openbas.database.model.Team;
import io.openbas.database.model.User;
import io.openbas.database.repository.TagRepository;
import io.openbas.database.repository.TeamRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exercise.form.ExerciseSimple;
import io.openbas.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openbas.rest.scenario.form.*;
import io.openbas.service.ImportService;
import io.openbas.service.ScenarioService;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static io.openbas.database.model.User.ROLE_ADMIN;
import static io.openbas.database.model.User.ROLE_USER;
import static io.openbas.helper.StreamHelper.fromIterable;

@RestController
@Secured(ROLE_USER)
@RequiredArgsConstructor
public class ScenarioApi {

  public static final String SCENARIO_URI = "/api/scenarios";

  private final ScenarioService scenarioService;
  private final TagRepository tagRepository;
  private final ImportService importService;

  private TeamRepository teamRepository;
  private UserRepository userRepository;

  @Autowired
  public void setTeamRepository(TeamRepository teamRepository) {
    this.teamRepository = teamRepository;
  }

  @Autowired
  public void setUserRepository(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostMapping(SCENARIO_URI)
  // TODO: Admin only ?
  public Scenario createScenario(@Valid @RequestBody final ScenarioInput input) {
    Scenario scenario = new Scenario();
    scenario.setUpdateAttributes(input);
    scenario.setTags(fromIterable(this.tagRepository.findAllById(input.getTagIds())));
    return this.scenarioService.createScenario(scenario);
  }

  @GetMapping(SCENARIO_URI)
  public List<ScenarioSimple> scenarios() {
    return this.scenarioService.scenarios();
  }

  @PostMapping("/api/scenarios/search")
  public Page<Scenario> scenarios(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.scenarioService.scenarios(searchPaginationInput);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  public Scenario scenario(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.scenario(scenarioId);
  }

  @GetMapping(SCENARIO_URI + "/external_reference/{externalReferenceId}")
  public Scenario scenarioByExternalId(@PathVariable @NotBlank final String externalReferenceId) {
    return scenarioService.scenarioByExternalReference(externalReferenceId);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario updateScenario(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    scenario.setUpdateAttributes(input);
    scenario.setTags(fromIterable(this.tagRepository.findAllById(input.getTagIds())));
    return this.scenarioService.updateScenario(scenario);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}/information")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario updateScenarioInformation(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioInformationInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    scenario.setUpdateAttributes(input);
    return this.scenarioService.updateScenario(scenario);
  }

  @DeleteMapping(SCENARIO_URI + "/{scenarioId}")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public void deleteScenario(@PathVariable @NotBlank final String scenarioId) {
    this.scenarioService.deleteScenario(scenarioId);
  }

  // -- TAGS --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/tags")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario updateScenarioTags(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTagsInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    scenario.setTags(fromIterable(this.tagRepository.findAllById(input.getTagIds())));
    return scenarioService.updateScenario(scenario);
  }

  // -- EXPORT --

  @GetMapping(SCENARIO_URI + "/{scenarioId}/export")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  public void exportScenario(
      @PathVariable @NotBlank final String scenarioId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    this.scenarioService.exportScenario(scenarioId, isWithTeams, isWithPlayers, isWithVariableValues, response);
  }

  // -- IMPORT --

  @PostMapping(SCENARIO_URI + "/import")
  @Secured(ROLE_ADMIN)
  public void importScenario(@RequestPart("file") @NotNull MultipartFile file) throws Exception {
    this.importService.handleFileImport(file);
  }

  // -- SIMULATION --

  // region scenarios
  @GetMapping(SCENARIO_URI + "/{scenarioId}/exercises")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  public Iterable<ExerciseSimple> scenarioExercises(@PathVariable @NotBlank final String scenarioId) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return scenario.getExercises().stream().map(ExerciseSimple::fromExercise).toList();
  }

  // endregion

  // -- TEAMS --

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/add")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Iterable<Team> addScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.addTeams(scenarioId, input.getTeamIds());
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/teams")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  public Iterable<Team> scenarioTeams(@PathVariable @NotBlank final String scenarioId) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return scenario.getTeams();
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/remove")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Iterable<Team> removeScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.removeTeams(scenarioId, input.getTeamIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/enable")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario enableScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.enablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario disableScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario addScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().addAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return this.scenarioService.enablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario removeScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  // -- RECURRENCE --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/recurrence")
  @PreAuthorize("isScenarioPlanner(#scenarioId)")
  public Scenario updateScenarioRecurrence(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioRecurrenceInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    scenario.setUpdateAttributes(input);
    return this.scenarioService.updateScenario(scenario);
  }

}
