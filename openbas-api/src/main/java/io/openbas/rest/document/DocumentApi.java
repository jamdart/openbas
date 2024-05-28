package io.openbas.rest.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.model.*;
import io.openbas.database.raw.RawDocument;
import io.openbas.database.repository.*;
import io.openbas.injectors.challenge.model.ChallengeContent;
import io.openbas.rest.document.form.DocumentCreateInput;
import io.openbas.rest.document.form.DocumentTagUpdateInput;
import io.openbas.rest.document.form.DocumentUpdateInput;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.FileService;
import io.openbas.service.InjectService;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.openbas.config.OpenBASAnonymous.ANONYMOUS;
import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.database.specification.DocumentSpecification.findGrantedFor;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;


@RestController
public class DocumentApi extends RestBehavior {

    private FileService fileService;
    private TagRepository tagRepository;
    private DocumentRepository documentRepository;
    private ExerciseRepository exerciseRepository;
    private ScenarioRepository scenarioRepository;
    private InjectService injectService;
    private InjectDocumentRepository injectDocumentRepository;
    private ChallengeRepository challengeRepository;
    private UserRepository userRepository;
    private CollectorRepository collectorRepository;

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    public void setInjectDocumentRepository(InjectDocumentRepository injectDocumentRepository) {
        this.injectDocumentRepository = injectDocumentRepository;
    }

    @Autowired
    public void setInjectService(InjectService injectService) {
        this.injectService = injectService;
    }

    @Autowired
    public void setExerciseRepository(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Autowired
    public void setScenarioRepository(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    @Autowired
    public void setTagRepository(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Autowired
    public void setDocumentRepository(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired
    public void setChallengeRepository(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    @Autowired
    public void setCollectorRepository(CollectorRepository collectorRepository) {
        this.collectorRepository = collectorRepository;
    }

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    private Optional<Document> resolveDocument(String documentId) {
        OpenBASPrincipal user = currentUser();
        if (user.isAdmin()) {
            return documentRepository.findById(documentId);
        } else {
            return documentRepository.findByIdGranted(documentId, user.getId());
        }
    }

    @PostMapping("/api/documents")
    public Document uploadDocument(@Valid @RequestPart("input") DocumentCreateInput input,
                                   @RequestPart("file") MultipartFile file) throws Exception {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String fileTarget = DigestUtils.md5Hex(file.getInputStream()) + "." + extension;
        Optional<Document> targetDocument = documentRepository.findByTarget(fileTarget);
        if (targetDocument.isPresent()) {
            Document document = targetDocument.get();
            // Compute exercises
            if (!document.getExercises().isEmpty()) {
                List<Exercise> exercises = new ArrayList<>(document.getExercises());
                List<Exercise> inputExercises = fromIterable(exerciseRepository.findAllById(input.getExerciseIds()));
                inputExercises.forEach(inputExercise -> {
                    if (!exercises.contains(inputExercise)) {
                        exercises.add(inputExercise);
                    }
                });
                document.setExercises(exercises);
            }
            // Compute scenarios
            if (!document.getScenarios().isEmpty()) {
                List<Scenario> scenarios = new ArrayList<>(document.getScenarios());
                List<Scenario> inputScenarios = fromIterable(scenarioRepository.findAllById(input.getScenarioIds()));
                inputScenarios.forEach(inputScenario -> {
                    if (!scenarios.contains(inputScenario)) {
                        scenarios.add(inputScenario);
                    }
                });
                document.setScenarios(scenarios);
            }
            // Compute tags
            List<Tag> tags = new ArrayList<>(document.getTags());
            List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
            inputTags.forEach(inputTag -> {
                if (!tags.contains(inputTag)) {
                    tags.add(inputTag);
                }
            });
            document.setTags(tags);
            return documentRepository.save(document);
        } else {
            fileService.uploadFile(fileTarget, file);
            Document document = new Document();
            document.setTarget(fileTarget);
            document.setName(file.getOriginalFilename());
            document.setDescription(input.getDescription());
            if (!input.getExerciseIds().isEmpty()) {
                document.setExercises(fromIterable(exerciseRepository.findAllById(input.getExerciseIds())));
            }
            if (!input.getScenarioIds().isEmpty()) {
                document.setScenarios(fromIterable(scenarioRepository.findAllById(input.getScenarioIds())));
            }
            document.setTags(fromIterable(tagRepository.findAllById(input.getTagIds())));
            document.setType(file.getContentType());
            return documentRepository.save(document);
        }
    }

    @GetMapping("/api/documents")
    public List<RawDocument> documents() {
        OpenBASPrincipal user = currentUser();
        if (user.isAdmin()) {
            return documentRepository.rawAllDocuments();
        } else {
            return documentRepository.rawAllDocumentsByAccessLevel(user.getId());
        }
    }

    @PostMapping("/api/documents/search")
    public Page<Document> searchDocuments(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
        OpenBASPrincipal user = currentUser();
        if (user.isAdmin()) {
            return buildPaginationJPA(
                    (Specification<Document> specification, Pageable pageable) -> this.documentRepository.findAll(
                            specification, pageable),
                    searchPaginationInput,
                    Document.class
            );
        } else {
            return buildPaginationJPA(
                (Specification<Document> specification, Pageable pageable) -> this.documentRepository.findAll(
                    findGrantedFor(user.getId()).and(specification),
                    pageable
                ),
                searchPaginationInput,
                Document.class
            );
        }
    }

    @GetMapping("/api/documents/{documentId}")
    public Document document(@PathVariable String documentId) {
        return resolveDocument(documentId).orElseThrow(ElementNotFoundException::new);
    }

    @GetMapping("/api/documents/{documentId}/tags")
    public List<Tag> documentTags(@PathVariable String documentId) {
        Document document = resolveDocument(documentId).orElseThrow(ElementNotFoundException::new);
        return document.getTags();
    }

    @PutMapping("/api/documents/{documentId}/tags")
    public Document documentTags(@PathVariable String documentId, @RequestBody DocumentTagUpdateInput input) {
        Document document = resolveDocument(documentId).orElseThrow(ElementNotFoundException::new);
        document.setTags(fromIterable(tagRepository.findAllById(input.getTagIds())));
        return documentRepository.save(document);
    }

    @Transactional(rollbackOn = Exception.class)
    @PutMapping("/api/documents/{documentId}")
    public Document updateDocumentInformation(@PathVariable String documentId,
                                              @Valid @RequestBody DocumentUpdateInput input) {
        Document document = resolveDocument(documentId).orElseThrow(ElementNotFoundException::new);
        document.setUpdateAttributes(input);
        document.setTags(fromIterable(tagRepository.findAllById(input.getTagIds())));

        // Get removed exercises
        Stream<String> askExerciseIdsStream = document.getExercises()
                .stream()
                .filter(exercise -> !exercise.isUserHasAccess(userRepository.findById(currentUser().getId()).orElseThrow(ElementNotFoundException::new)))
                .map(Exercise::getId);
        List<String> askExerciseIds = Stream.concat(askExerciseIdsStream, input.getExerciseIds().stream()).distinct().toList();
        List<Exercise> removedExercises = document.getExercises().stream()
                .filter(exercise -> !askExerciseIds.contains(exercise.getId())).toList();
        document.setExercises(fromIterable(exerciseRepository.findAllById(askExerciseIds)));
        // In case of exercise removal, all inject doc attachment for exercise
        removedExercises.forEach(exercise -> injectService.cleanInjectsDocExercise(exercise.getId(), documentId));

        // Get removed scenarios
        Stream<String> askScenarioIdsStream = document.getScenarios().stream()
                .filter(scenario -> !scenario.isUserHasAccess(userRepository.findById(currentUser().getId()).orElseThrow(ElementNotFoundException::new)))
                .map(Scenario::getId);
        List<String> askScenarioIds = Stream.concat(askScenarioIdsStream, input.getScenarioIds().stream()).distinct().toList();
        List<Scenario> removedScenarios = document.getScenarios().stream()
                .filter(scenario -> !askScenarioIds.contains(scenario.getId())).toList();
        document.setScenarios(fromIterable(scenarioRepository.findAllById(askScenarioIds)));
        // In case of scenario removal, all inject doc attachment for scenario
        removedScenarios.forEach(scenario -> injectService.cleanInjectsDocScenario(scenario.getId(), documentId));

        // Save and return
        return documentRepository.save(document);
    }

    @GetMapping("/api/documents/{documentId}/file")
    public void downloadDocument(@PathVariable String documentId, HttpServletResponse response) throws IOException {
        Document document = resolveDocument(documentId).orElseThrow(ElementNotFoundException::new);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + document.getName());
        response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
        response.setStatus(HttpServletResponse.SC_OK);
        try (InputStream fileStream = fileService.getFile(document).orElseThrow(ElementNotFoundException::new)) {
            fileStream.transferTo(response.getOutputStream());
        }
    }

    @GetMapping(value = "/api/images/injectors/{injectType}", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody ResponseEntity<byte[]> getInjectorImage(@PathVariable String injectType) throws IOException {
        Optional<InputStream> fileStream = fileService.getInjectorImage(injectType);
        if (fileStream.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(IOUtils.toByteArray(fileStream.get()));
        }
        return null;
    }

    @GetMapping(value = "/api/images/collectors/{collectorId}", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody ResponseEntity<byte[]> getCollectorImage(@PathVariable String collectorId) throws IOException {
        Optional<InputStream> fileStream = fileService.getCollectorImage(collectorId);
        if (fileStream.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(IOUtils.toByteArray(fileStream.get()));
        }
        return null;
    }

    @GetMapping(value = "/api/images/collectors/id/{collectorId}", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody ResponseEntity<byte[]> getCollectorImageFromId(@PathVariable String collectorId) throws IOException {
        Collector collector = this.collectorRepository.findById(collectorId).orElseThrow(ElementNotFoundException::new);
        Optional<InputStream> fileStream = fileService.getCollectorImage(collector.getType());
        if (fileStream.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(IOUtils.toByteArray(fileStream.get()));
        }
        return null;
    }

    @GetMapping(value = "/api/images/executors/{executorId}", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody ResponseEntity<byte[]> getExecutorImage(@PathVariable String executorId) throws IOException {
        Optional<InputStream> fileStream = fileService.getExecutorImage(executorId);
        if (fileStream.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(IOUtils.toByteArray(fileStream.get()));
        }
        return null;
    }

    private List<Document> getExercisePlayerDocuments(Exercise exercise) {
        List<Article> articles = exercise.getArticles();
        List<Inject> injects = exercise.getInjects();
        return getPlayerDocuments(articles, injects);
    }

    private List<Document> getScenarioPlayerDocuments(Scenario scenario) {
        List<Article> articles = scenario.getArticles();
        List<Inject> injects = scenario.getInjects();
        return getPlayerDocuments(articles, injects);
    }

    private List<Document> getPlayerDocuments(List<Article> articles, List<Inject> injects) {
        Stream<Document> channelsDocs = articles.stream()
                .map(Article::getChannel)
                .flatMap(channel -> channel.getLogos().stream());
        Stream<Document> articlesDocs = articles.stream()
                .flatMap(article -> article.getDocuments().stream());
        List<String> challenges = injects.stream()
                .filter(inject -> inject.getInjectorContract().getId().equals(CHALLENGE_PUBLISH))
                .filter(inject -> inject.getContent() != null)
                .flatMap(inject -> {
                    try {
                        ChallengeContent content = mapper.treeToValue(inject.getContent(), ChallengeContent.class);
                        return content.getChallenges().stream();
                    } catch (JsonProcessingException e) {
                        return Stream.empty();
                    }
                })
                .toList();
        Stream<Document> challengesDocs = fromIterable(challengeRepository.findAllById(challenges)).stream()
                .flatMap(challenge -> challenge.getDocuments().stream());
        return Stream.of(channelsDocs, articlesDocs, challengesDocs).flatMap(documentStream -> documentStream).distinct()
                .toList();
    }

    @Transactional(rollbackOn = Exception.class)
    @DeleteMapping("/api/documents/{documentId}")
    public void deleteDocument(@PathVariable String documentId) {
        injectDocumentRepository.deleteDocumentFromAllReferences(documentId);
        List<Document> documents = documentRepository.removeById(documentId);
        documents.forEach(document -> {
            try {
                fileService.deleteFile(document.getTarget());
            } catch (Exception e) {
                // Fail no longer available in the storage.
            }
        });
    }

    // -- EXERCISE & SENARIO--

    @GetMapping("/api/player/{exerciseOrScenarioId}/documents")
    public List<Document> playerDocuments(@PathVariable String exerciseOrScenarioId, @RequestParam Optional<String> userId) {
        Optional<Exercise> exerciseOpt = this.exerciseRepository.findById(exerciseOrScenarioId);
        Optional<Scenario> scenarioOpt = this.scenarioRepository.findById(exerciseOrScenarioId);

        final User user = impersonateUser(userRepository, userId);
        if (user.getId().equals(ANONYMOUS)) {
            throw new UnsupportedOperationException("User must be logged or dynamic player is required");
        }

        if (exerciseOpt.isPresent()) {
            if (!exerciseOpt.get().isUserHasAccess(user) && !exerciseOpt.get().getUsers().contains(user)) {
                throw new UnsupportedOperationException("The given player is not in this exercise");
            }
            return getExercisePlayerDocuments(exerciseOpt.get());
        } else if (scenarioOpt.isPresent()) {
            if (!scenarioOpt.get().isUserHasAccess(user) && !scenarioOpt.get().getUsers().contains(user)) {
                throw new UnsupportedOperationException("The given player is not in this exercise");
            }
            return getScenarioPlayerDocuments(scenarioOpt.get());
        } else {
            throw new IllegalArgumentException("Exercise or scenario ID not found");
        }
    }

    @GetMapping("/api/player/{exerciseOrScenarioId}/documents/{documentId}/file")
    public void downloadPlayerDocument(
            @PathVariable String exerciseOrScenarioId,
            @PathVariable String documentId,
            @RequestParam Optional<String> userId, HttpServletResponse response) throws IOException {
        Optional<Exercise> exerciseOpt = this.exerciseRepository.findById(exerciseOrScenarioId);
        Optional<Scenario> scenarioOpt = this.scenarioRepository.findById(exerciseOrScenarioId);

        final User user = impersonateUser(userRepository, userId);
        if (user.getId().equals(ANONYMOUS)) {
            throw new UnsupportedOperationException("User must be logged or dynamic player is required");
        }

        Document document = null;
        if (exerciseOpt.isPresent()) {
            if (!exerciseOpt.get().isUserHasAccess(user) && !exerciseOpt.get().getUsers().contains(user)) {
                throw new UnsupportedOperationException("The given player is not in this exercise");
            }
            document = getExercisePlayerDocuments(exerciseOpt.get())
                    .stream()
                    .filter(doc -> doc.getId().equals(documentId))
                    .findFirst()
                    .orElseThrow(ElementNotFoundException::new);
        } else if (scenarioOpt.isPresent()) {
            if (!scenarioOpt.get().isUserHasAccess(user) && !scenarioOpt.get().getUsers().contains(user)) {
                throw new UnsupportedOperationException("The given player is not in this exercise");
            }
            document = getScenarioPlayerDocuments(scenarioOpt.get())
                    .stream()
                    .filter(doc -> doc.getId().equals(documentId))
                    .findFirst()
                    .orElseThrow(ElementNotFoundException::new);
        }

        if (document != null) {
            response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + document.getName());
            response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
            response.setStatus(HttpServletResponse.SC_OK);
            try (InputStream fileStream = fileService.getFile(document).orElseThrow(ElementNotFoundException::new)) {
                fileStream.transferTo(response.getOutputStream());
            }
        }
    }

}
