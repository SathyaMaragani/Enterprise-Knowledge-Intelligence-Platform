import { useAuth } from '../auth/AuthContext.jsx';

export default function HomePage() {
  const { session } = useAuth();

  return (
    <section>
      <h1>Welcome, {session.username}</h1>
      <p className="muted">
        You are signed in. The dashboard, document repository and search are not built yet.
      </p>
    </section>
  );
}
