import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

// Gates protected routes: no user -> redirect to login.
export default function ProtectedRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return children;
}
