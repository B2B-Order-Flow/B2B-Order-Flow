import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { createQueryClient } from './api/queryClient';
import { AppProviders } from './App';
import { createAppRouter } from './routes/router';

const queryClient = createQueryClient();
const router = createAppRouter();

const root = document.getElementById('root');
if (!root) throw new Error('Нет элемента #root');

createRoot(root).render(
  <StrictMode>
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
