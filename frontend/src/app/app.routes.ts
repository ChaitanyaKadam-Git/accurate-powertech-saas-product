import { Routes } from '@angular/router';
import { adminGuard, authGuard, permGuard } from './auth';
import { LoginComponent } from './login.component';
import { SetPasswordComponent } from './set-password.component';
import { DashboardComponent } from './dashboard.component';

// Sign-in and the dashboard load with the app; every other page is fetched the first time it is opened.
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'set-password', component: SetPasswordComponent },
  {
    path: '', canActivate: [authGuard], children: [
      { path: '', component: DashboardComponent },
      { path: 'statements', loadComponent: () => import('./quote-list.component').then(m => m.QuoteListComponent), canActivate: [permGuard('QUOTATIONS', 'VIEW')] },
      { path: 'quote/new', loadComponent: () => import('./quote-form.component').then(m => m.QuoteFormComponent), canActivate: [permGuard('QUOTATIONS', 'CREATE')] },
      { path: 'quote/:id', loadComponent: () => import('./quote-form.component').then(m => m.QuoteFormComponent), canActivate: [permGuard('QUOTATIONS', 'VIEW')] },
      { path: 'enquiries', loadComponent: () => import('./enquiries.component').then(m => m.EnquiriesComponent), canActivate: [permGuard('ENQUIRIES', 'VIEW')] },
      { path: 'enquiries/:id', loadComponent: () => import('./enquiry-detail.component').then(m => m.EnquiryDetailComponent), canActivate: [permGuard('ENQUIRIES', 'VIEW')] },
      { path: 'customers/:id', loadComponent: () => import('./customer-detail.component').then(m => m.CustomerDetailComponent), canActivate: [permGuard('CUSTOMERS', 'VIEW')] },
      { path: 'customers', loadComponent: () => import('./customers.component').then(m => m.CustomersComponent), canActivate: [permGuard('CUSTOMERS', 'VIEW')] },
      { path: 'templates', loadComponent: () => import('./templates.component').then(m => m.TemplatesComponent), canActivate: [permGuard('TEMPLATE_DATA', 'VIEW')] },
      { path: 'parts', loadComponent: () => import('./parts.component').then(m => m.PartsComponent), canActivate: [permGuard('PARTS', 'VIEW')] },
      { path: 'products', loadComponent: () => import('./products.component').then(m => m.ProductsComponent), canActivate: [permGuard('PRODUCTS', 'VIEW')] },
      { path: 'settings', loadComponent: () => import('./sections.component').then(m => m.SectionsComponent) },
      {
        path: 'admin', canActivate: [adminGuard], children: [
          { path: '', loadComponent: () => import('./admin-overview.component').then(m => m.AdminOverviewComponent) },
          { path: 'users', loadComponent: () => import('./admin-users.component').then(m => m.AdminUsersComponent) },
          { path: 'users/:id', loadComponent: () => import('./admin-user-detail.component').then(m => m.AdminUserDetailComponent) },
          { path: 'access', loadComponent: () => import('./admin-access.component').then(m => m.AdminAccessComponent) },
          { path: 'roles', loadComponent: () => import('./admin-roles.component').then(m => m.AdminRolesComponent) },
          { path: 'activity', loadComponent: () => import('./admin-activity.component').then(m => m.AdminActivityComponent) },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
