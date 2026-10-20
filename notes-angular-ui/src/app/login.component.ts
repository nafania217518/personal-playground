import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  template: `
    <main class="card">
      <h1>{{ registerMode() ? 'Register' : 'Login' }}</h1>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <label for="username">Username</label>
        <input id="username" formControlName="username" autocomplete="username" />
        <label for="password">Password</label>
        <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <button type="submit" [disabled]="form.invalid">{{ registerMode() ? 'Register' : 'Login' }}</button>
        <button type="button" class="link" (click)="registerMode.set(!registerMode())">
          {{ registerMode() ? 'Have an account? Login' : 'No account? Register' }}
        </button>
      </form>
    </main>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly registerMode = signal(false);
  readonly error = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit() {
    const { username, password } = this.form.getRawValue();
    this.error.set('');
    const login$ = this.auth.login(username, password);
    const flow$ = this.registerMode()
      ? this.auth.register(username, password).pipe(switchMap(() => login$))
      : login$;
    flow$.subscribe({
      next: () => this.router.navigate(['/notes']),
      error: (err) =>
        this.error.set(err.status === 401 ? 'Invalid username or password' : err.status === 409 ? 'Username is taken' : 'Request failed'),
    });
  }
}
