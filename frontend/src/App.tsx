import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { LoginPage } from "@/pages/auth/LoginPage";
import { RegisterPage } from "@/pages/auth/RegisterPage";
import { DashboardPage } from "@/pages/dashboard/DashboardPage";
import { TransactionsPage } from "@/pages/transactions/TransactionsPage";
import { NewDisputePage } from "@/pages/disputes/NewDisputePage";
import { DisputesPage } from "@/pages/disputes/DisputesPage";
import { AccountPage } from "@/pages/account/AccountPage";

export function App() {
  const { isAuthenticated } = useAuth();
  return (
    <Routes>
      <Route path="/" element={<Navigate to={isAuthenticated ? "/dashboard" : "/auth/login"} replace />} />
      <Route path="/auth/login" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route path="/auth/register" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <RegisterPage />} />
      <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="/transactions" element={<ProtectedRoute><TransactionsPage /></ProtectedRoute>} />
      <Route path="/disputes" element={<ProtectedRoute><DisputesPage /></ProtectedRoute>} />
      <Route path="/disputes/new" element={<ProtectedRoute><NewDisputePage /></ProtectedRoute>} />
      <Route path="/account" element={<ProtectedRoute><AccountPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}