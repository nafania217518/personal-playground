import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('login stores token in localStorage and exposes isLoggedIn signal as true', () => {
    service.login('bob', 'secret').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'bob', password: 'secret' });
    req.flush({ token: 'abc', expiresIn: 3600 });
    expect(localStorage.getItem('token')).toBe('abc');
    expect(service.isLoggedIn()).toBe(true);
  });

  it('logout clears token and isLoggedIn becomes false', () => {
    service.login('bob', 'secret').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush({ token: 'abc', expiresIn: 3600 });
    service.logout();
    expect(localStorage.getItem('token')).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
  });

  it('register posts username and password', () => {
    service.register('bob', 'secret').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'bob', password: 'secret' });
    req.flush({ id: 1, username: 'bob', roles: ['USER'] });
  });

  it('token getter returns null when not logged in', () => {
    expect(service.token).toBeNull();
  });
});
