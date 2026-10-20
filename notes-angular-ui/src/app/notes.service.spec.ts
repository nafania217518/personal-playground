import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Note, NotesService } from './notes.service';
import { environment } from '../environments/environment';

const note: Note = { id: 1, owner: 'bob', title: 't', content: 'c', createdAt: 'a', updatedAt: 'b' };

describe('NotesService', () => {
  let service: NotesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(NotesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('list sends q, page and size as query params and returns the page', () => {
    let result: unknown;
    service.list('abc', 2, 10).subscribe((r) => (result = r));
    const req = http.expectOne((r) => r.url === `${environment.apiUrl}/notes`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('abc');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    const page = { content: [note], page: { size: 10, number: 2, totalElements: 1, totalPages: 1 } };
    req.flush(page);
    expect(result).toEqual(page);
  });

  it('create posts title and content', () => {
    service.create('t', 'c').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/notes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 't', content: 'c' });
    req.flush(note);
  });

  it('update puts to /notes/{id}', () => {
    service.update(1, 't', 'c').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/notes/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ title: 't', content: 'c' });
    req.flush(note);
  });

  it('remove deletes /notes/{id}', () => {
    service.remove(1).subscribe();
    const req = http.expectOne(`${environment.apiUrl}/notes/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
