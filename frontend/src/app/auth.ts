import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { BrandService } from './brand';

export type Portal = 'ADMIN' | 'EMPLOYEE';
export interface Signed { token: string; companyName: string; name: string; portal: Portal; mustChangePassword: boolean; permissions: string[]; }
export interface Me {
  id: number; name: string; email: string; companyName: string; portal: Portal; mustChangePassword: boolean;
  seesAllQuotations: boolean; permissions: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private injector = inject(Injector);

  /** Rights of the signed-in person, e.g. "QUOTATIONS:CREATE". Rebuilt from /api/me after every sign-in or page load. */
  private perms = signal<Set<string>>(new Set(JSON.parse(localStorage.getItem('perms') ?? '[]')));
  portal = signal<Portal>((localStorage.getItem('portal') as Portal) ?? 'EMPLOYEE');
  mustChange = signal(localStorage.getItem('mustChange') === '1');
  userName = signal(localStorage.getItem('userName') ?? '');
  isAdmin = computed(() => this.portal() === 'ADMIN');

  get token() { return localStorage.getItem('jwt'); }
  get company() { return localStorage.getItem('company') ?? ''; }
  get loggedIn() { return !!this.token; }

  /** The menus and buttons use this; the server enforces the same rules on every API call. */
  can(module: string, right: string) { return this.isAdmin() || this.perms().has(`${module}:${right}`); }
  canAny(module: string, ...rights: string[]) { return rights.some(r => this.can(module, r)); }

  login(email: string, password: string) {
    return this.http.post<Signed>('/api/auth/login', { email, password }).pipe(tap(r => this.store(r)));
  }
  register(email: string, password: string, companyName: string) {
    return this.http.post<Signed>('/api/auth/register', { email, password, companyName }).pipe(tap(r => this.store(r)));
  }
  me() {
    return this.http.get<Me>('/api/me').pipe(tap(m => {
      this.store({ token: this.token!, companyName: m.companyName, name: m.name, portal: m.portal, mustChangePassword: m.mustChangePassword, permissions: m.permissions });
    }));
  }
  passwordChanged() { localStorage.setItem('mustChange', '0'); this.mustChange.set(false); }

  logout() {
    const done = () => {
      ['jwt', 'company', 'perms', 'portal', 'mustChange', 'userName'].forEach(k => localStorage.removeItem(k));
      this.perms.set(new Set()); this.mustChange.set(false);
      this.injector.get(BrandService).clear();
      this.router.navigate(['/login']);
    };
    if (!this.token) return done();
    // tell the server so the token stops working and the sign-out time is recorded
    this.http.post('/api/auth/logout', {}).subscribe({ next: done, error: done });
  }

  private store(r: Signed) {
    localStorage.setItem('jwt', r.token);
    localStorage.setItem('company', r.companyName ?? '');
    localStorage.setItem('perms', JSON.stringify(r.permissions ?? []));
    localStorage.setItem('portal', r.portal);
    localStorage.setItem('mustChange', r.mustChangePassword ? '1' : '0');
    localStorage.setItem('userName', r.name ?? '');
    this.perms.set(new Set(r.permissions ?? []));
    this.portal.set(r.portal);
    this.mustChange.set(r.mustChangePassword);
    this.userName.set(r.name ?? '');
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const r = auth.token ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } }) : req;
  return next(r).pipe(catchError(e => {
    if (e.status === 401 && !req.url.includes('/api/auth')) {
      localStorage.removeItem('jwt');     // expired, signed out elsewhere, or the account was deactivated
      auth.logout();
    } else if (e.status === 403 && e.error?.message === 'PASSWORD_CHANGE_REQUIRED') {
      auth.mustChange.set(true);
      router.navigate(['/set-password']);
    }
    return throwError(() => e);
  }));
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService), router = inject(Router);
  if (!auth.loggedIn) return router.createUrlTree(['/login']);
  if (auth.mustChange()) return router.createUrlTree(['/set-password']);
  return true;
};

export const adminGuard: CanActivateFn = () => inject(AuthService).isAdmin() || inject(Router).createUrlTree(['/']);

/** Route guard factory: permGuard('CUSTOMERS', 'VIEW'). */
export const permGuard = (module: string, right: string): CanActivateFn => () =>
  inject(AuthService).can(module, right) || inject(Router).createUrlTree(['/']);
