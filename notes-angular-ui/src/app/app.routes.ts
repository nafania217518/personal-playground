import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { LoginComponent } from './login.component';
import { NotesComponent } from './notes.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'notes', component: NotesComponent, canActivate: [authGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'notes' },
];
