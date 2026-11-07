import React from 'react';
import { BrowserRouter, Navigate, Route, Routes, Link, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { LenderDashboard } from './pages/LenderDashboard';
import { Marketplace } from './pages/Marketplace';
import { MyLeases } from './pages/MyLeases';
import { AdminPage } from './pages/AdminPage';
import './styles.css';

const ProtectedRoute: React.FC<{ children: React.ReactNode; adminOnly?: boolean }> = ({
  children,
  adminOnly = false,
}) => {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return <div className="main-content"><p>Loading session...</p></div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (adminOnly && user?.role !== 'ADMIN') {
    return <Navigate to="/leases" replace />;
  }

  return <>{children}</>;
};

const Navigation: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return null;
  }

  return (
    <nav className="navbar">
      <div className="brand">
        Mini<span>DC</span>
      </div>
      <div className="nav-links">
        <Link to="/leases" className={location.pathname === '/leases' ? 'active' : ''}>
          My Leases
        </Link>
        <Link to="/market" className={location.pathname === '/market' ? 'active' : ''}>
          Marketplace
        </Link>
        <Link to="/lender" className={location.pathname === '/lender' ? 'active' : ''}>
          Lender Hub
        </Link>
        {user?.role === 'ADMIN' && (
          <Link to="/admin" className={location.pathname === '/admin' ? 'active' : ''}>
            Admin
          </Link>
        )}
        <span className="user-email text-muted"><small>({user?.email})</small></span>
        <button className="btn btn-secondary btn-sm" onClick={logout}>
          Sign Out
        </button>
      </div>
    </nav>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="app-container">
          <Navigation />
          <main className="main-content">
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route
                path="/leases"
                element={
                  <ProtectedRoute>
                    <MyLeases />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/market"
                element={
                  <ProtectedRoute>
                    <Marketplace />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/lender"
                element={
                  <ProtectedRoute>
                    <LenderDashboard />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin"
                element={
                  <ProtectedRoute adminOnly>
                    <AdminPage />
                  </ProtectedRoute>
                }
              />
              <Route path="*" element={<Navigate to="/leases" replace />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;

