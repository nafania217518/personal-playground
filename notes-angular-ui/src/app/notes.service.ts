import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

export interface Note {
  id: number;
  owner: string;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface NotePage {
  content: Note[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

@Injectable({ providedIn: 'root' })
export class NotesService {
  private http = inject(HttpClient);
  private url = `${environment.apiUrl}/notes`;

  list(q = '', page = 0, size = 20) {
    return this.http.get<NotePage>(this.url, { params: { q, page, size } });
  }

  create(title: string, content: string) {
    return this.http.post<Note>(this.url, { title, content });
  }

  update(id: number, title: string, content: string) {
    return this.http.put<Note>(`${this.url}/${id}`, { title, content });
  }

  remove(id: number) {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
