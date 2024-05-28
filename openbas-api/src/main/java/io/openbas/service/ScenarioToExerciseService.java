package io.openbas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.injectors.channel.ChannelContract;
import io.openbas.injectors.channel.model.ChannelContent;
import io.openbas.service.VariableService;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ScenarioToExerciseService {

    @Resource
    protected ObjectMapper mapper;

    private final ExerciseRepository exerciseRepository;
    private final GrantRepository grantRepository;
    private final TeamRepository teamRepository;
    private final ExerciseTeamUserRepository exerciseTeamUserRepository;
    private final ObjectiveRepository objectiveRepository;
    private final DocumentRepository documentRepository;
    private final ArticleRepository articleRepository;
    private final LessonsCategoryRepository lessonsCategoryRepository;
    private final LessonsQuestionRepository lessonsQuestionRepository;
    private final InjectRepository injectRepository;
    private final InjectDocumentRepository injectDocumentRepository;
    private final VariableService variableService;

    @Transactional(rollbackFor = Exception.class)
    public Exercise toExercise(
            @NotBlank final Scenario scenario,
            @Nullable final Instant start) {
        Exercise exercise = new Exercise();
        exercise.setScenario(scenario);
        exercise.setName(scenario.getName());
        exercise.setDescription(scenario.getDescription());
        exercise.setSubtitle(scenario.getSubtitle());
        exercise.setCategory(scenario.getCategory());
        exercise.setMainFocus(scenario.getMainFocus());
        exercise.setSeverity(scenario.getSeverity());
        exercise.setHeader(scenario.getHeader());
        exercise.setFooter(scenario.getFooter());
        exercise.setFrom(scenario.getFrom());
        exercise.addReplyTos(scenario.getReplyTos());
        exercise.setStart(start);

        // Tags
        exercise.setTags(copy(scenario.getTags(), Tag.class));

        Exercise exerciseSaved = this.exerciseRepository.save(exercise);

        // Grants
        List<Grant> exerciseGrants = scenario.getGrants().stream()
                .map(scenarioGrant -> {
                    Grant grant = new Grant();
                    grant.setName(scenarioGrant.getName());
                    grant.setGroup(scenarioGrant.getGroup());
                    grant.setExercise(exerciseSaved);
                    return this.grantRepository.save(grant);
                })
                .toList();
        exerciseSaved.setGrants(exerciseGrants);

        // Teams
        Map<String, Team> contextualTeams = new HashMap<>();
        scenario.getTeams().forEach(scenarioTeam -> {
            if (scenarioTeam.getContextual()) {
                Team team = new Team();
                team.setName(scenarioTeam.getName());
                team.setDescription(scenarioTeam.getDescription());
                team.setTags(copy(scenarioTeam.getTags(), Tag.class));
                team.setOrganization(scenarioTeam.getOrganization());

                team.setUsers(copy(scenarioTeam.getUsers(), User.class));
                team.setExercises(new ArrayList<>() {{
                    add(exerciseSaved);
                }});
                team.setContextual(scenarioTeam.getContextual());
                Team teamSaved = this.teamRepository.save(team);
                contextualTeams.put(scenarioTeam.getId(), teamSaved);
            } else {
                List<Exercise> exercises = scenarioTeam.getExercises();
                exercises.add(exercise);
                scenarioTeam.setExercises(exercises);
                this.teamRepository.save(scenarioTeam);
            }
        });

        // TeamUsers
        List<ExerciseTeamUser> exerciseTeamUsers = scenario.getTeamUsers().stream()
                .map(scenarioTeamUser -> {
                    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
                    exerciseTeamUser.setExercise(exerciseSaved);
                    exerciseTeamUser.setUser(scenarioTeamUser.getUser());
                    exerciseTeamUser.setTeam(computeTeam(scenarioTeamUser.getTeam(), contextualTeams));
                    return exerciseTeamUser;
                })
                .toList();
        this.exerciseTeamUserRepository.saveAll(exerciseTeamUsers);

        // Objectives
        List<Objective> scenarioObjectives = scenario.getObjectives();
        List<Objective> exerciseObjectives = scenarioObjectives.stream()
                .map(scenarioObjective -> {
                    Objective exerciseObjective = new Objective();
                    exerciseObjective.setExercise(exerciseSaved);
                    exerciseObjective.setTitle(scenarioObjective.getTitle());
                    exerciseObjective.setDescription(scenarioObjective.getDescription());
                    exerciseObjective.setPriority(scenarioObjective.getPriority());
                    return exerciseObjective;
                })
                .toList();
        this.objectiveRepository.saveAll(exerciseObjectives);

        // Documents
        List<Document> scenarioDocuments = addExerciseToDocuments(scenario.getDocuments(), exerciseSaved);
        this.documentRepository.saveAll(scenarioDocuments);

        // Articles
        Map<String, Article> articles = new HashMap<>();
        List<Article> scenarioArticles = scenario.getArticles();
        List<Article> exerciseArticles = scenarioArticles.stream()
                .map(scenarioArticle -> {
                    Article exerciseArticle = new Article();
                    exerciseArticle.setName(scenarioArticle.getName());
                    exerciseArticle.setContent(scenarioArticle.getContent());
                    exerciseArticle.setAuthor(scenarioArticle.getAuthor());
                    exerciseArticle.setShares(scenarioArticle.getShares());
                    exerciseArticle.setLikes(scenarioArticle.getLikes());
                    exerciseArticle.setComments(scenarioArticle.getComments());
                    exerciseArticle.setExercise(exerciseSaved);
                    exerciseArticle.setChannel(scenarioArticle.getChannel());

                    List<Document> articleDocuments = addExerciseToDocuments(scenarioArticle.getDocuments(), exerciseSaved);
                    exerciseArticle.setDocuments(articleDocuments);
                    articles.put(scenarioArticle.getId(), exerciseArticle);
                    return exerciseArticle;
                })
                .toList();
        this.articleRepository.saveAll(exerciseArticles);

        // Lessons categories
        List<LessonsCategory> scenarioLessonCategories = scenario.getLessonsCategories();
        scenarioLessonCategories.forEach(scenarioLessonCategory -> {
            LessonsCategory exerciseLessonCategory = new LessonsCategory();
            exerciseLessonCategory.setExercise(exerciseSaved);
            exerciseLessonCategory.setName(scenarioLessonCategory.getName());
            exerciseLessonCategory.setDescription(scenarioLessonCategory.getDescription());
            exerciseLessonCategory.setOrder(scenarioLessonCategory.getOrder());

            // Teams
            List<Team> teams = new ArrayList<>();
            scenarioLessonCategory.getTeams().forEach(team -> teams.add(computeTeam(team, contextualTeams)));
            exerciseLessonCategory.setTeams(teams);

            LessonsCategory exerciseLessonCategorySaved = this.lessonsCategoryRepository.save(exerciseLessonCategory);

            // Lessons questions
            List<LessonsQuestion> exerciseLessonsQuestions = scenarioLessonCategory.getQuestions()
                    .stream()
                    .map(scenarioLessonsQuestion -> {
                        LessonsQuestion exerciseLessonsQuestion = new LessonsQuestion();
                        exerciseLessonsQuestion.setContent(scenarioLessonsQuestion.getContent());
                        exerciseLessonsQuestion.setExplanation(scenarioLessonsQuestion.getExplanation());
                        exerciseLessonsQuestion.setOrder(scenarioLessonsQuestion.getOrder());
                        exerciseLessonsQuestion.setCategory(exerciseLessonCategorySaved);
                        return exerciseLessonsQuestion;
                    })
                    .toList();
            this.lessonsQuestionRepository.saveAll(exerciseLessonsQuestions);
        });

        // Injects
        List<Inject> scenarioInjects = scenario.getInjects();
        scenarioInjects.forEach(scenarioInject -> {
            Inject exerciseInject = new Inject();
            exerciseInject.setTitle(scenarioInject.getTitle());
            exerciseInject.setDescription(scenarioInject.getDescription());
            exerciseInject.setInjectorContract(scenarioInject.getInjectorContract());
            exerciseInject.setCountry(scenarioInject.getCountry());
            exerciseInject.setCity(scenarioInject.getCity());
            exerciseInject.setEnabled(scenarioInject.isEnabled());
            exerciseInject.setAllTeams(scenarioInject.isAllTeams());
            exerciseInject.setExercise(exerciseSaved);
            exerciseInject.setDependsDuration(scenarioInject.getDependsDuration());
            exerciseInject.setUser(scenarioInject.getUser());
            exerciseInject.setStatus(scenarioInject.getStatus().orElse(null));
            exerciseInject.setTags(copy(scenarioInject.getTags(), Tag.class));

            // Content
            if (ChannelContract.TYPE.equals(scenarioInject.getInjectorContract().getInjector().getType())) {
                try {
                    ChannelContent content = mapper.treeToValue(scenarioInject.getContent(), ChannelContent.class);
                    content.setArticles(
                            content.getArticles().stream().map(articleId -> articles.get(articleId).getId()).toList()
                    );
                    exerciseInject.setContent(mapper.valueToTree(content));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                exerciseInject.setContent(scenarioInject.getContent());
            }

            // Teams
            List<Team> teams = new ArrayList<>();
            scenarioInject.getTeams().forEach(team -> teams.add(computeTeam(team, contextualTeams)));
            exerciseInject.setTeams(teams);

            // Assets & Asset Groups
            exerciseInject.setAssets(copy(scenarioInject.getAssets(), Asset.class));
            exerciseInject.setAssetGroups(copy(scenarioInject.getAssetGroups(), AssetGroup.class));
            Inject injectSaved = this.injectRepository.save(exerciseInject);

            // Documents
            List<InjectDocument> exerciseInjectDocuments = new ArrayList<>();
            scenarioInject.getDocuments().forEach(injectDocument -> {
                InjectDocument exerciseInjectDocument = new InjectDocument();
                exerciseInjectDocument.setInject(injectSaved);
                exerciseInjectDocument.setDocument(injectDocument.getDocument());
                exerciseInjectDocument.setAttached(injectDocument.isAttached());
                exerciseInjectDocuments.add(exerciseInjectDocument);
            });
            this.injectDocumentRepository.saveAll(exerciseInjectDocuments);
        });

        // Variables
        List<Variable> scenarioVariables = this.variableService.variablesFromScenario(scenario.getId());
        List<Variable> exerciseVariables = scenarioVariables.stream()
                .map(scenarioVariable -> {
                    Variable exerciseVariable = new Variable();
                    exerciseVariable.setKey(scenarioVariable.getKey());
                    exerciseVariable.setValue(scenarioVariable.getValue());
                    exerciseVariable.setDescription(scenarioVariable.getDescription());
                    exerciseVariable.setType(scenarioVariable.getType());
                    exerciseVariable.setExercise(exerciseSaved);
                    return exerciseVariable;
                })
                .toList();
        this.variableService.createVariables(exerciseVariables);

        return exerciseSaved;
    }

    private <T extends Base> List<T> copy(@NotNull final List<T> origins, Class<T> clazz) {
        List<T> destinations = new ArrayList<>();
        origins.forEach(origin -> {
            try {
                T destination = clazz.getDeclaredConstructor().newInstance();
                BeanUtils.copyProperties(destination, origin);
                destinations.add(destination);
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
        return destinations;
    }

    private List<Document> addExerciseToDocuments(
            @NotNull final List<Document> origDocuments,
            @NotNull final Exercise exercise) {
        List<Document> destDocuments = new ArrayList<>();
        origDocuments.forEach(origDocument -> {
            try {
                Document destDocument = new Document();
                BeanUtils.copyProperties(destDocument, origDocument);
                List<Exercise> exercises = destDocument.getExercises();
                exercises.add(exercise);
                destDocument.setExercises(exercises);
                destDocuments.add(destDocument);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
        return destDocuments;
    }

    private Team computeTeam(@NotNull final Team origTeam, @NotNull final Map<String, Team> contextualTeams) {
        if (origTeam.getContextual()) {
            return contextualTeams.get(origTeam.getId());
        } else {
            return origTeam;
        }
    }

}
