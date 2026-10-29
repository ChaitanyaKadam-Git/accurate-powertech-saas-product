import { Routes } from '@angular/router';
import { adminGuard, authGuard, permGuard } from './auth';
import { LoginComponent } from './login.component';
import { SetPasswordComponent } from './set-password.component';
import { DashboardComponent } from './dashboard.component';
import { QuoteListComponent } from './quote-list.component';
import { QuoteFormComponent } from './quote-form.component';
import { SectionsComponent } from './sections.component';
import { CustomersComponent } from './customers.component';
import { ProductsComponent } from './products.component';
import { PartsComponent } from './parts.component';
import { AdminOverviewComponent } from './admin-overview.component';
import { AdminUsersComponent } from './admin-users.component';
import { AdminUserDetailComponent } from './admin-user-detail.component';
import { AdminRolesComponent } from './admin-roles.component';
import { AdminActivityComponent } from './admin-activity.component';
import { AdminAccessComponent } from './admin-access.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'set-password', component: SetPasswordComponent },
  {
    path: '', canActivate: [authGuard], children: [
      { path: '', component: DashboardComponent },
      { path: 'statements', component: QuoteListComponent, canActivate: [permGuard('QUOTATIONS', 'VIEW')] },
      { path: 'quote/new', component: QuoteFormComponent, canActivate: [permGuard('QUOTATIONS', 'CREATE')] },
      { path: 'quote/:id', component: QuoteFormComponent, canActivate: [permGuard('QUOTATIONS', 'VIEW')] },
      { path: 'parts', component: PartsComponent, canActivate: [permGuard('PRODUCTS', 'VIEW')] },
      { path: 'products', component: ProductsComponent, canActivate: [permGuard('PRODUCTS', 'VIEW')] },
      { path: 'customers', component: CustomersComponent, canActivate: [permGuard('CUSTOMERS', 'VIEW')] },
      { path: 'settings', component: SectionsComponent },
      {
        path: 'admin', canActivate: [adminGuard], children: [
          { path: '', component: AdminOverviewComponent },
          { path: 'users', component: AdminUsersComponent },
          { path: 'users/:id', component: AdminUserDetailComponent },
          { path: 'access', component: AdminAccessComponent },
          { path: 'roles', component: AdminRolesComponent },
          { path: 'activity', component: AdminActivityComponent },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
