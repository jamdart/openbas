import React, { useState } from 'react';
import { IconButton, Menu, MenuItem } from '@mui/material';
import { MoreVert } from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useFormatter } from '../../../../components/i18n';
import type { Endpoint, EndpointInput } from '../../../../utils/api-types';
import EndpointForm from './EndpointForm';
import { useAppDispatch } from '../../../../utils/hooks';
import { deleteEndpoint, updateEndpoint2 } from '../../../../actions/assets/endpoint-actions';
import Drawer from '../../../../components/common/Drawer';
import DialogDelete from '../../../../components/common/DialogDelete';
import { updateAssetsOnAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import Dialog from '../../../../components/common/Dialog';
import { EndpointStoreWithType } from './EndpointsList';
import type { EndpointStore } from './Endpoint';

interface Props {
  inline?: boolean;
  endpoint: EndpointStoreWithType;
  assetGroupId?: string;
  assetGroupEndpointIds?: string[];
  onRemoveEndpointFromInject?: (assetId: string) => void;
  onRemoveEndpointFromAssetGroup?: (assetId: string) => void;
  openEditOnInit?: boolean;
  onUpdate?: (result: EndpointStore) => void;
  onDelete?: (result: string) => void;
}

const EndpointPopover: React.FC<Props> = ({
  inline,
  endpoint,
  assetGroupId,
  assetGroupEndpointIds,
  onRemoveEndpointFromInject,
  onRemoveEndpointFromAssetGroup,
  openEditOnInit = false,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);

  const initialValues = (({
    asset_name,
    asset_description,
    asset_last_seen,
    asset_tags,
    endpoint_hostname,
    endpoint_ips,
    endpoint_mac_addresses,
    endpoint_platform,
  }) => ({
    asset_name,
    asset_description,
    asset_last_seen: asset_last_seen ?? undefined,
    asset_tags,
    endpoint_hostname,
    endpoint_ips,
    endpoint_mac_addresses: endpoint_mac_addresses ?? [],
    endpoint_platform,
  }))(endpoint);

  // Edition
  const [edition, setEdition] = useState(openEditOnInit);

  const handleEdit = () => {
    setEdition(true);
    setAnchorEl(null);
  };

  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: ({ assetId, data }: { assetId: Endpoint['asset_id'], data: EndpointInput }) => updateEndpoint2(assetId, data),
    onSuccess: (data) => {
      console.log('data', data);
      // queryClient.setQueriesData permets la manip du cache mais il y a des cas où faire de l'invalidation de datas et du coup que le système refasse une requete sera plus cohérent
      queryClient.setQueriesData({ queryKey: ['endpoints'] }, (oldData) => {
        console.log('oldData', oldData);
        console.log('dfdf', oldData.data.content.findIndex((endpoint) => endpoint.asset_id === data.data.asset_id));
        console.log('dzeazeezfdf', oldData.data.content.toSpliced(oldData.data.content.findIndex((endpoint) => endpoint.asset_id === data.data.asset_id), 1, data.data));
        return { ...oldData, data: { ...oldData.data, content: oldData.data.content.toSpliced(oldData.data.content.findIndex((endpoint) => endpoint.asset_id === data.data.asset_id), 1, data.data) } };
      });
    },
  });

  const submitEdit = async (data: EndpointInput) => {
    // dispatch(updateEndpoint(endpoint.asset_id, data)).then(
    //   (result: { result: string, entities: { endpoints: Record<string, EndpointStore> } }) => {
    //     if (result.entities) {
    //       if (onUpdate) {
    //         const endpointUpdated = result.entities.endpoints[result.result];
    //         onUpdate(endpointUpdated);
    //       }
    //     }
    //     return result;
    //   },
    // );
    await mutation.mutate({ assetId: endpoint.asset_id, data });
    setEdition(false);
  };

  // Removal
  const [removalFromAssetGroup, setRemovalFromAssetGroup] = useState(false);

  const handleRemoveFromAssetGroup = () => {
    setRemovalFromAssetGroup(true);
    setAnchorEl(null);
  };
  const submitRemoveFromAssetGroup = () => {
    if (assetGroupId) {
      dispatch(
        updateAssetsOnAssetGroup(assetGroupId, {
          asset_group_assets: assetGroupEndpointIds?.filter((id) => id !== endpoint.asset_id),
        }),
      ).then(() => {
        if (onRemoveEndpointFromAssetGroup) {
          onRemoveEndpointFromAssetGroup(endpoint.asset_id);
        }
        setRemovalFromAssetGroup(false);
      });
    }
  };

  // Deletion
  const [deletion, setDeletion] = useState(false);

  const handleDelete = () => {
    setDeletion(true);
    setAnchorEl(null);
  };
  const submitDelete = () => {
    dispatch(deleteEndpoint(endpoint.asset_id)).then(
      () => {
        if (onDelete) {
          onDelete(endpoint.asset_id);
        }
      },
    );
    setDeletion(false);
  };

  return (
    <>
      <IconButton
        color="primary"
        onClick={(ev) => {
          ev.stopPropagation();
          setAnchorEl(ev.currentTarget);
        }}
        aria-label={`endpoint menu for ${endpoint.asset_name}`}
        aria-haspopup="true"
        size="large"
      >
        <MoreVert />
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        <MenuItem onClick={handleEdit}>
          {t('Update')}
        </MenuItem>
        {(assetGroupId && endpoint.type !== 'dynamic') && (
          <MenuItem onClick={handleRemoveFromAssetGroup}>
            {t('Remove from the asset group')}
          </MenuItem>
        )}
        {onRemoveEndpointFromInject && (
          <MenuItem onClick={() => onRemoveEndpointFromInject(endpoint.asset_id)}>
            {t('Remove from the inject')}
          </MenuItem>
        )}
        <MenuItem onClick={handleDelete}>
          {t('Delete')}
        </MenuItem>
      </Menu>

      {inline ? (
        <Dialog
          open={edition}
          handleClose={() => setEdition(false)}
          title={t('Update the endpoint')}
        >
          <EndpointForm
            initialValues={initialValues}
            editing={true}
            onSubmit={submitEdit}
            handleClose={() => setEdition(false)}
          />
        </Dialog>
      ) : (
        <Drawer
          open={edition}
          handleClose={() => setEdition(false)}
          title={t('Update the endpoint')}
        >
          <EndpointForm
            initialValues={initialValues}
            editing
            onSubmit={submitEdit}
            handleClose={() => setEdition(false)}
          />
        </Drawer>
      )}
      <DialogDelete
        open={removalFromAssetGroup}
        handleClose={() => setRemovalFromAssetGroup(false)}
        handleSubmit={submitRemoveFromAssetGroup}
        text={t('Do you want to remove the endpoint from the asset group ?')}
      />
      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete the endpoint ?')}
      />
    </>
  );
};

export default EndpointPopover;
