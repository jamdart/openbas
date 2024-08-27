import filterableProperties from '../../../../actions/schema/schema-action';
import { convertJsonClassToJavaClass } from './FilterUtils';
import type { PropertySchemaDTO } from '../../../../utils/api-types';

const useFilterableProperties: (entityPrefix: string, filterNames: string[]) => Promise<PropertySchemaDTO[]> = (entity_prefix: string, filterNames: string[]) => {
  if (filterNames.length === 0) {
    return Promise.resolve([]);
  }
  const javaClass = convertJsonClassToJavaClass(entity_prefix);
  return filterableProperties(javaClass, filterNames).then(((result: { data: PropertySchemaDTO[] }) => {
    const propertySchemas: PropertySchemaDTO[] = result.data;
    if (filterNames.some((s) => s.endsWith('_kill_chain_phases'))) {
      propertySchemas.push({
        schema_property_name: `${entity_prefix}_kill_chain_phases`,
        schema_property_type_array: true,
        schema_property_values: [],
        schema_property_has_dynamic_value: true,
        schema_property_type: 'string',
      });
    }
    if (filterNames.some((s) => s.endsWith('scenario_platforms'))) {
      propertySchemas.push({
        schema_property_name: 'scenario_platforms',
        schema_property_type_array: true,
        schema_property_values: ['Linux', 'Windows', 'MacOS', 'Container', 'Service', 'Generic', 'Internal', 'Unknow'],
        schema_property_has_dynamic_value: false,
        schema_property_type: 'string',
      });
    }
    if (filterNames.some((s) => s.endsWith('inject_type'))) {
      propertySchemas.push({
        schema_property_name: 'inject_type',
        schema_property_type_array: false,
        schema_property_values: undefined,
        schema_property_has_dynamic_value: false,
        schema_property_type: 'string',
      });
    }
    return propertySchemas;
  }));
};

export default useFilterableProperties;
