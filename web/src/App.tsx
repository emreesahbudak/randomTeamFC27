import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Header } from "./components/Header";
import { AuthBootstrap } from "./components/AuthBootstrap";
import { RequireAuth, RequireAdmin } from "./components/RouteGuards";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { ProfilePage } from "./pages/ProfilePage";
import { LeaguePage } from "./pages/LeaguePage";
import { AdminTeamsPage } from "./pages/AdminTeamsPage";

export function App() {
  return (
    <BrowserRouter>
      <AuthBootstrap>
        <div className="min-h-screen bg-stage-bg">
          <Header />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/profile"
              element={
                <RequireAuth>
                  <ProfilePage />
                </RequireAuth>
              }
            />
            <Route
              path="/league"
              element={
                <RequireAuth>
                  <LeaguePage />
                </RequireAuth>
              }
            />
            <Route
              path="/admin/teams"
              element={
                <RequireAdmin>
                  <AdminTeamsPage />
                </RequireAdmin>
              }
            />
          </Routes>
        </div>
      </AuthBootstrap>
    </BrowserRouter>
  );
}
