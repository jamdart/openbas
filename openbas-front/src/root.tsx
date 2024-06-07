import React, { lazy, Suspense, useEffect } from 'react';
import * as R from 'ramda';
import { Navigate, Route, Routes } from 'react-router-dom';
import { StyledEngineProvider } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useHelper } from './store';
import { fetchMe, fetchPlatformParameters } from './actions/Application';
import NotFound from './components/NotFound';
import ConnectedThemeProvider from './components/AppThemeProvider';
import ConnectedIntlProvider from './components/AppIntlProvider';
import { errorWrapper } from './components/Error';
import { useAppDispatch } from './utils/hooks';
import type { LoggedHelper } from './actions/helper';
import Loader from './components/Loader';
import { UserContext } from './utils/hooks/useAuth';

const RootPublic = lazy(() => import('./public/Root'));
const IndexPrivate = lazy(() => import('./private/Index'));
const IndexAdmin = lazy(() => import('./admin/Index'));
const Comcheck = lazy(() => import('./public/components/comcheck/Comcheck'));
const Channel = lazy(() => import('./public/components/channels/Channel'));
const Challenges = lazy(() => import('./public/components/challenges/Challenges'));
const Lessons = lazy(() => import('./public/components/lessons/Lessons'));

const queryClient = new QueryClient();

const Root = () => {
  const { logged, me, settings } = useHelper((helper: LoggedHelper) => {
    return { logged: helper.logged(), me: helper.getMe(), settings: helper.getPlatformSettings() };
  });
  const dispatch = useAppDispatch();
  useEffect(() => {
    dispatch(fetchMe());
    dispatch(fetchPlatformParameters());
  }, []);
  if (R.isEmpty(logged)) {
    return <div />;
  }
  if (!logged || !me || !settings) {
    return (
      <Suspense fallback={<Loader />}>
        <RootPublic />
      </Suspense>
    );
  }
  return (
    <UserContext.Provider
      value={{
        me,
        settings,
      }}
    >
      <QueryClientProvider client={queryClient}>
        <StyledEngineProvider injectFirst={true}>
          <ConnectedIntlProvider>
            <ConnectedThemeProvider>
              <CssBaseline />
              <Suspense fallback={<Loader />}>
                <Routes>
                  <Route path="" element={logged.isOnlyPlayer ? <Navigate to="private" replace={true} />
                    : <Navigate to="admin" replace={true} />}
                  />
                  <Route path="private/*" element={errorWrapper(IndexPrivate)()} />
                  <Route path="admin/*" element={errorWrapper(IndexAdmin)()} />
                  {/* Routes from /public/Index that need to be accessible for logged user are duplicated here */}
                  <Route path="comcheck/:statusId" element={errorWrapper(Comcheck)()} />
                  <Route path="channels/:exerciseId/:channelId" element={errorWrapper(Channel)()} />
                  <Route path="challenges/:exerciseId" element={errorWrapper(Challenges)()} />
                  <Route path="lessons/:exerciseId" element={errorWrapper(Lessons)()} />
                  {/* Not found */}
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </Suspense>
            </ConnectedThemeProvider>
          </ConnectedIntlProvider>
        </StyledEngineProvider>
      </QueryClientProvider>
    </UserContext.Provider>
  );
};

export default Root;
