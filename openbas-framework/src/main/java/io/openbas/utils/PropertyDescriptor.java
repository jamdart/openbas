package io.openbas.utils;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PropertyDescriptor {
  private String jsonPath;
  private Class<?> clazz;

  public PropertyDescriptor(String jsonPath, Class<?> clazz) {
    this.jsonPath = jsonPath;
    this.clazz = clazz;
  }
}
