import { Component, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, skip, startWith, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { Note, NotesService } from './notes.service';

@Component({
  selector: 'app-notes',
  imports: [ReactiveFormsModule],
  template: `
    <main class="card">
      <header>
        <h1>Notes</h1>
        <button type="button" class="logout" (click)="logout()">Logout</button>
      </header>

      <label for="search">Search</label>
      <input id="search" type="search" [value]="query()" (input)="query.set($any($event.target).value)" />

      <form class="create" [formGroup]="form" (ngSubmit)="create()">
        <label for="title">Title</label>
        <input id="title" formControlName="title" />
        <label for="content">Content</label>
        <textarea id="content" formControlName="content" rows="3"></textarea>
        <button type="submit" [disabled]="form.invalid">Add note</button>
      </form>

      <ul>
        @for (note of notes(); track note.id) {
          <li class="note">
            <div>
              <strong>{{ note.title }}</strong>
              <p>{{ note.content }}</p>
            </div>
            <button type="button" class="delete" (click)="remove(note)">Delete</button>
          </li>
        } @empty {
          <li class="empty">No notes</li>
        }
      </ul>
    </main>
  `,
})
export class NotesComponent {
  private api = inject(NotesService);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly query = signal('');
  readonly notes = signal<Note[]>([]);
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', Validators.required],
    content: [''],
  });

  constructor() {
    toObservable(this.query)
      .pipe(
        skip(1), // initial value loads at once, only typing is debounced
        debounceTime(300),
        startWith(''),
        distinctUntilChanged(),
        switchMap((q) => this.api.list(q, 0, 20)),
      )
      .subscribe((page) => this.notes.set(page.content));
  }

  create() {
    const { title, content } = this.form.getRawValue();
    this.api.create(title, content).subscribe((note) => {
      this.notes.update((list) => [...list, note]);
      this.form.reset();
    });
  }

  remove(note: Note) {
    this.api.remove(note.id).subscribe(() => this.notes.update((list) => list.filter((n) => n.id !== note.id)));
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
