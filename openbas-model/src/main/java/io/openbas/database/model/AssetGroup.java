package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.database.model.Filters.FilterGroup;
import io.openbas.helper.MultiIdDeserializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

@Data
@Entity
@Table(name = "asset_groups")
@EntityListeners(ModelBaseListener.class)
public class AssetGroup implements Base {

  @Id
  @Column(name = "asset_group_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("asset_group_id")
  @NotBlank
  private String id;

  @NotBlank
  @Column(name = "asset_group_name")
  @JsonProperty("asset_group_name")
  @Queryable(searchable = true, sortable = true)
  private String name;

  @Column(name = "asset_group_description")
  @JsonProperty("asset_group_description")
  @Queryable(sortable = true)
  private String description;

  // -- ASSET --

  @Type(JsonType.class)
  @Column(name = "asset_group_dynamic_filter")
  @JsonProperty("asset_group_dynamic_filter")
  private FilterGroup dynamicFilter;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "asset_groups_assets",
      joinColumns = @JoinColumn(name = "asset_group_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_id"))
  @JsonSerialize(using = MultiIdDeserializer.class)
  @JsonProperty("asset_group_assets")
  private List<Asset> assets = new ArrayList<>();

  @Getter(NONE)
  @Transient
  @JsonProperty("asset_group_dynamic_assets")
  private List<Asset> dynamicAssets = new ArrayList<>();

  // Getter is Mandatory when we use @Transient annotation
  @JsonSerialize(using = MultiIdDeserializer.class)
  public List<Asset> getDynamicAssets() {
    return this.dynamicAssets;
  }

  // -- TAG --

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "asset_groups_tags",
      joinColumns = @JoinColumn(name = "asset_group_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdDeserializer.class)
  @JsonProperty("asset_group_tags")
  @Queryable(sortable = true)
  private List<Tag> tags = new ArrayList<>();

  // -- AUDIT --

  @Column(name = "asset_group_created_at")
  @JsonProperty("asset_group_created_at")
  private Instant createdAt = now();

  @Column(name = "asset_group_updated_at")
  @JsonProperty("asset_group_updated_at")
  private Instant updatedAt = now();

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
