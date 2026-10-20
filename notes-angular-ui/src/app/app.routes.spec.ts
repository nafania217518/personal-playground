import { routes } from './app.routes';
import { authGuard } from './auth.guard';
import { NotesComponent } from './notes.component';
import { LoginComponent } from './login.component';

describe('routes', () => {
  it('notes route is protected by authGuard', () => {
    const notes = routes.find((r) => r.path === 'notes')!;
    expect(notes.component).toBe(NotesComponent);
    expect(notes.canActivate).toContain(authGuard);
  });

  it('login route and default redirect exist', () => {
    expect(routes.find((r) => r.path === 'login')!.component).toBe(LoginComponent);
    expect(routes.find((r) => r.path === '')!.redirectTo).toBe('notes');
  });
});
