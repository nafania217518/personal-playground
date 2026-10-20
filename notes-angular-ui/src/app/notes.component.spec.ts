import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { NotesComponent } from './notes.component';
import { Note, NotesService } from './notes.service';
import { AuthService } from './auth.service';

const mk = (id: number, title: string): Note => ({
  id, owner: 'bob', title, content: 'c', createdAt: 'a', updatedAt: 'b',
});
const page = (content: Note[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } });

describe('NotesComponent', () => {
  let fixture: ComponentFixture<NotesComponent>;
  let notes: { list: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> };
  let auth: { logout: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.fn>;

  const el = <T extends Element>(sel: string) => fixture.nativeElement.querySelector(sel) as T;
  const all = (sel: string) => fixture.nativeElement.querySelectorAll(sel) as NodeListOf<Element>;
  const setInput = async (sel: string, value: string) => {
    const input = el<HTMLInputElement>(sel);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    notes = { list: vi.fn().mockReturnValue(of(page([mk(1, 'first'), mk(2, 'second')]))), create: vi.fn(), remove: vi.fn() };
    auth = { logout: vi.fn() };
    navigate = vi.fn().mockResolvedValue(true);
    await TestBed.configureTestingModule({
      imports: [NotesComponent],
      providers: [
        { provide: NotesService, useValue: notes },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NotesComponent);
    await fixture.whenStable();
  });

  afterEach(() => vi.useRealTimers());

  it('loads notes on init and renders one row per note', () => {
    expect(notes.list).toHaveBeenCalledWith('', 0, 20);
    expect(all('.note').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('first');
  });

  it('typing in search reloads notes with q after debounce', async () => {
    vi.useFakeTimers();
    const input = el<HTMLInputElement>('#search');
    input.value = 'sec';
    input.dispatchEvent(new Event('input'));
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(299);
    expect(notes.list).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(notes.list).toHaveBeenCalledWith('sec', 0, 20);
  });

  it('creating a note appends it to the list and clears the form', async () => {
    notes.create.mockReturnValue(of(mk(3, 'third')));
    await setInput('#title', 'third');
    await setInput('#content', 'body');
    el<HTMLFormElement>('form.create').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(notes.create).toHaveBeenCalledWith('third', 'body');
    expect(all('.note').length).toBe(3);
    expect(el<HTMLInputElement>('#title').value).toBe('');
  });

  it('deleting a note removes it from the list', async () => {
    notes.remove.mockReturnValue(of(undefined));
    el<HTMLButtonElement>('.note button.delete').click();
    await fixture.whenStable();
    expect(notes.remove).toHaveBeenCalledWith(1);
    expect(all('.note').length).toBe(1);
  });

  it('logout button calls AuthService.logout and navigates to /login', async () => {
    el<HTMLButtonElement>('button.logout').click();
    await fixture.whenStable();
    expect(auth.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
