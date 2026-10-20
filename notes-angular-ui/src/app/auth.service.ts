import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { environment } from '../environments/environment';

const KEY = 'token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private tokenSig = signal<string | null>(localStorage.getItem(KEY));
  readonly isLoggedIn = () => this.tokenSig() !== null;

  get token() {
    return this.tokenSig();
  }

  login(username: string, password: string) {
    return this.http
      .post<{ token: string; expiresIn: number }>(`${environment.apiUrl}/auth/login`, { username, password })
      .pipe(
        tap(({ token }) => {
          localStorage.setItem(KEY, token);
          this.tokenSig.set(token);
        }),
      );
  }

  register(username: string, password: string) {
    return this.http.post<{ id: number; username: string; roles: string[] }>(
      `${environment.apiUrl}/auth/register`,
      { username, password },
    );
  }

  logout() {
    localStorage.removeItem(KEY);
    this.tokenSig.set(null);
  }
}
