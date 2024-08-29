import { Switch } from '@mui/material';
import React, { FunctionComponent } from 'react';
import { useFormatter } from '../../../../components/i18n';
import { FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import type { FilterGroup } from '../../../../utils/api-types';

export const INJECTOR_CONTRACT_INJECTOR_FILTER_KEY = 'injector_contract_injector';

export const INJECTOR_CONTRACT_PLAYERS_ONLY = [
  '49229430-b5b5-431f-ba5b-f36f599b0233', // Challenge
  '8d932e36-353c-48fa-ba6f-86cb7b02ed19', // Channel
  '41b4dd55-5bd1-4614-98cd-9e3770753306', // Email
  '6981a39d-e219-4016-a235-cf7747994abc', // Manual
  'e5aefbca-cf8f-4a57-9384-0503a8ffc22f', // SMS
];

interface Props {
  filterHelpers: FilterHelpers;
  filterGroup?: FilterGroup;
}

const InjectorContractSwitchFilter: FunctionComponent<Props> = ({
  filterHelpers,
  filterGroup,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const isChecked = () => {
    const filter = filterGroup?.filters?.find((f) => f.key === INJECTOR_CONTRACT_INJECTOR_FILTER_KEY);
    if (!filter) {
      return false;
    }
    return filter.values?.some((v) => INJECTOR_CONTRACT_PLAYERS_ONLY.includes(v));
  };

  const [enablePlayerFilter, setEnablePlayerFilter] = React.useState(isChecked);

  const onChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { checked } = event.target;
    setEnablePlayerFilter(checked);
    if (checked) {
      filterHelpers.handleAddMultipleValueFilter(
        INJECTOR_CONTRACT_INJECTOR_FILTER_KEY,
        INJECTOR_CONTRACT_PLAYERS_ONLY,
      );
    } else {
      filterHelpers.handleAddMultipleValueFilter(
        INJECTOR_CONTRACT_INJECTOR_FILTER_KEY,
        [],
      );
    }
  };

  return (
    <>
      <Switch
        checked={enablePlayerFilter}
        onChange={onChange}
        color="primary"
        size="small"
      />
      <span>{t('Targets players only')}</span>
    </>
  );
};

export default InjectorContractSwitchFilter;
