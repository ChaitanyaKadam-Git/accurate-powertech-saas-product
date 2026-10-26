import { Routes } from '@angular/router';
import { authGuard } from './auth';
import { LoginComponent } from './login.component';
import { DashboardComponent } from './dashboard.component';
import { QuoteListComponent } from './quote-list.component';
import { QuoteFormComponent } from './quote-form.component';
import { SectionsComponent } from './sections.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '', canActivate: [authGuard], children: [
      { path: '', component: DashboardComponent },
      { path: 'statements', component: QuoteListComponent },
      { path: 'quote/new', component: QuoteFormComponent },
      { path: 'quote/:id', component: QuoteFormComponent },
      { path: 'settings', component: SectionsComponent },
    ],
  },
  { path: '**', redirectTo: '' },
];
