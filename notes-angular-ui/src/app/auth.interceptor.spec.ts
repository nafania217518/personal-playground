import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  it('adds Authorization header when token exists', () => {
    localStorage.setItem('token', 'abc');
    http.get('/notes').subscribe();
    const req = ctrl.expectOne('/notes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
  });

  it('does not add header when no token', () => {
    http.get('/notes').subscribe();
    expect(ctrl.expectOne('/notes').request.headers.has('Authorization')).toBe(false);
  });

  it('on 401 response logs out and navigates to /login', () => {
    localStorage.setItem('token', 'abc');
    const auth = TestBed.inject(AuthService);
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    http.get('/notes').subscribe({ error: () => {} });
    ctrl.expectOne('/notes').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBe(false);
    expect(nav).toHaveBeenCalledWith(['/login']);
  });
});
