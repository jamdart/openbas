import Immutable from 'seamless-immutable';
import { FORM_ERROR } from 'final-form';
import * as R from 'ramda';
import * as Constants from '../constants/ActionTypes';
import { api } from '../network';
import { MESSAGING$ } from './Environment';

const isEmptyPath = R.isNil(window.BASE_PATH) || R.isEmpty(window.BASE_PATH);
const contextPath = isEmptyPath || window.BASE_PATH === '/' ? '' : window.BASE_PATH;
export const APP_BASE_PATH = isEmptyPath || contextPath.startsWith('/') ? contextPath : `/${contextPath}`;

export const buildUri = (uri) => `${APP_BASE_PATH}${uri}`;

const buildError = (data) => {
  const errorsExtractor = R.pipe(
    R.pathOr({}, ['errors', 'children']),
    R.toPairs(),
    R.map((elem) => {
      const extractErrorsPipe = R.pipe(
        R.tail(),
        R.head(),
        R.propOr([], 'errors'),
        R.head(),
      );
      return [R.head(elem), extractErrorsPipe(elem)];
    }),
    R.fromPairs(),
    R.set(R.lensProp(FORM_ERROR), data.message),
  );
  return errorsExtractor(data);
};

const apiInstance = api();

export const simpleCall = (uri) => apiInstance.get(buildUri(uri));
export const simplePostCall = (uri, data) => apiInstance.post(buildUri(uri), data);
export const simplePutCall = (uri, data) => apiInstance.put(buildUri(uri), data);
export const simpleDelCall = (uri, data) => apiInstance.delete(buildUri(uri), data);
export const getReferential = (schema, uri, noloading) => (dispatch) => {
  if (noloading !== true) {
    dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  }
  return api(schema)
    .get(buildUri(uri))
    .then((response) => {
      dispatch({ type: Constants.DATA_FETCH_SUCCESS, payload: response.data });
      return response.data;
    })
    .catch((error) => {
      dispatch({ type: Constants.DATA_FETCH_ERROR, payload: error });
      throw error;
    });
};

export const putReferential = (schema, uri, data) => (dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .put(buildUri(uri), data)
    .then((response) => {
      dispatch({ type: Constants.DATA_FETCH_SUCCESS, payload: response.data });
      dispatch({ type: Constants.DATA_UPDATE_SUCCESS, payload: response.data });
      MESSAGING$.notifySuccess('The element has been updated');
      return response.data;
    })
    .catch((error) => {
      dispatch({ type: Constants.DATA_FETCH_ERROR, payload: error });
      if (error.status === 409) {
        MESSAGING$.notifyError('The element already exists');
      }
      return buildError(error);
    });
};

export const postReferential = (schema, uri, data) => (dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .post(buildUri(uri), data)
    .then((response) => {
      dispatch({ type: Constants.DATA_FETCH_SUCCESS, payload: response.data });
      return response.data;
    })
    .catch((error) => {
      dispatch({ type: Constants.DATA_FETCH_ERROR, payload: error });
      if (error.status === 409) {
        MESSAGING$.notifyError('The element already exists');
      }
      if (error.status === 500) {
        MESSAGING$.notifyError('Internal error');
      }
      return buildError(error);
    });
};

export const delReferential = (uri, type, id) => (dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api()
    .delete(buildUri(uri))
    .then(() => {
      dispatch({
        type: Constants.DATA_DELETE_SUCCESS,
        payload: Immutable({ type, id }),
      });
    })
    .catch((error) => {
      dispatch({ type: Constants.DATA_FETCH_ERROR, payload: error });
      throw error;
    });
};
