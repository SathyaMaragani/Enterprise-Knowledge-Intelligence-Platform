import { Navigate, Route, Routes } from 'react-router';
import { RequireAuth } from './auth/AuthContext.jsx';
import Layout from './components/Layout.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import DocumentPage from './pages/DocumentPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RepositoryPage from './pages/RepositoryPage.jsx';
import SearchPage from './pages/SearchPage.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="repository" element={<RepositoryPage />} />
        <Route path="documents/:id" element={<DocumentPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
