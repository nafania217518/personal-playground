import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let auth: { login: ReturnType<typeof vi.fn>; register: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.fn>;

  const el = <T extends Element>(sel: string) => fixture.nativeElement.querySelector(sel) as T;
  const setInput = async (sel: string, value: string) => {
    const input = el<HTMLInputElement>(sel);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    auth = { login: vi.fn(), register: vi.fn() };
    navigate = vi.fn().mockResolvedValue(true);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    await fixture.whenStable();
  });

  it('submit button is disabled while form is invalid', async () => {
    expect(el<HTMLButtonElement>('button[type=submit]').disabled).toBe(true);
    await setInput('#username', 'bob');
    await setInput('#password', 'secret');
    expect(el<HTMLButtonElement>('button[type=submit]').disabled).toBe(false);
  });

  it('valid submit calls AuthService.login and navigates to /notes', async () => {
    auth.login.mockReturnValue(of({ token: 'abc', expiresIn: 1 }));
    await setInput('#username', 'bob');
    await setInput('#password', 'secret');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(auth.login).toHaveBeenCalledWith('bob', 'secret');
    expect(navigate).toHaveBeenCalledWith(['/notes']);
  });

  it('shows error message when login fails with 401', async () => {
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    await setInput('#username', 'bob');
    await setInput('#password', 'wrong');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(el('.error').textContent).toContain('Invalid username or password');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('register mode calls register then login', async () => {
    auth.register.mockReturnValue(of({ id: 1, username: 'bob', roles: [] }));
    auth.login.mockReturnValue(of({ token: 'abc', expiresIn: 1 }));
    el<HTMLButtonElement>('button[type=button]').click();
    await fixture.whenStable();
    await setInput('#username', 'bob');
    await setInput('#password', 'secret');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(auth.register).toHaveBeenCalledWith('bob', 'secret');
    expect(auth.login).toHaveBeenCalledWith('bob', 'secret');
    expect(navigate).toHaveBeenCalledWith(['/notes']);
  });
});
