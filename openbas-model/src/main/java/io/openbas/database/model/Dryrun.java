package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.MonoIdDeserializer;
import io.openbas.helper.MultiIdDeserializer;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "dryruns")
@EntityListeners(ModelBaseListener.class)
public class Dryrun implements Base {
    @Id
    @Column(name = "dryrun_id")
    @GeneratedValue(generator = "UUID")
    @UuidGenerator
    @JsonProperty("dryrun_id")
    @NotBlank
    private String id;

    @Column(name = "dryrun_name")
    @JsonProperty("dryrun_name")
    private String name;

    @Column(name = "dryrun_speed")
    @JsonProperty("dryrun_speed")
    private int speed;

    @Column(name = "dryrun_date")
    @JsonProperty("dryrun_date")
    private Instant date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dryrun_exercise")
    @JsonSerialize(using = MonoIdDeserializer.class)
    @JsonProperty("dryrun_exercise")
    private Exercise exercise;

    @OneToMany(mappedBy = "run", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DryInject> injects = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "dryruns_users",
            joinColumns = @JoinColumn(name = "dryrun_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @JsonSerialize(using = MultiIdDeserializer.class)
    @JsonProperty("dryrun_users")
    private List<User> users = new ArrayList<>();

    // region transient
    @JsonProperty("dryrun_finished")
    public boolean isFinished() {
        List<DryInject> injects = getInjects();
        return injects.stream().allMatch(dryInject -> dryInject.getStatus() != null);
    }

    @JsonProperty("dryrun_users_number")
    public long getUsersNumber() {
        return getUsers().size();
    }

    @JsonProperty("dryrun_start_date")
    public Optional<Instant> getRunStart() {
        return getInjects().stream().min(DryInject.executionComparator).flatMap(DryInject::getDate);
    }

    @JsonProperty("dryrun_end_date")
    public Optional<Instant> getRunEnd() {
        return getInjects().stream().max(DryInject.executionComparator).flatMap(DryInject::getDate);
    }
    // endregion

    public String getId() {
        return id;
    }

    @Override
    public boolean isUserHasAccess(User user) {
        return exercise.isUserHasAccess(user);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public List<DryInject> getInjects() {
        return injects;
    }

    public void setInjects(List<DryInject> injects) {
        this.injects = injects;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
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
