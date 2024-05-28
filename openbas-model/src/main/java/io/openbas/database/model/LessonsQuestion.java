package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.MonoIdDeserializer;
import io.openbas.helper.MultiIdDeserializer;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.time.Instant.now;

@Getter
@Setter
@Entity
@Table(name = "lessons_questions")
@EntityListeners(ModelBaseListener.class)
public class LessonsQuestion implements Base {
    @Id
    @Column(name = "lessons_question_id")
    @GeneratedValue(generator = "UUID")
    @UuidGenerator
    @JsonProperty("lessonsquestion_id")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lessons_question_category")
    @JsonSerialize(using = MonoIdDeserializer.class)
    @JsonProperty("lessons_question_category")
    private LessonsCategory category;

    @Column(name = "lessons_question_created_at")
    @JsonProperty("lessons_question_created_at")
    private Instant created = now();

    @Column(name = "lessons_question_updated_at")
    @JsonProperty("lessons_question_updated_at")
    private Instant updated = now();

    @Column(name = "lessons_question_content")
    @JsonProperty("lessons_question_content")
    private String content;

    @Column(name = "lessons_question_explanation")
    @JsonProperty("lessons_question_explanation")
    private String explanation;

    @Column(name = "lessons_question_order")
    @JsonProperty("lessons_question_order")
    private int order;

    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
    @JsonProperty("lessons_question_answers")
    @JsonSerialize(using = MultiIdDeserializer.class)
    private List<LessonsAnswer> answers = new ArrayList<>();

    // region transient
    @JsonProperty("lessons_question_exercise")
    public String getExercise() {
        return Optional.ofNullable(getCategory().getExercise()).map(Exercise::getId).orElse(null);
    }
    // endregion

    @Override
    public boolean isUserHasAccess(User user) {
        return getCategory().getExercise().isUserHasAccess(user);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
        Base base = (Base) o;
        return id.equals(base.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
