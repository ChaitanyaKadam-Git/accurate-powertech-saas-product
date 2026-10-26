import { Injectable, Injector, inject } from '@angular/core';
import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { BrandService } from './brand';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private injector = inject(Injector);

  get token() { return localStorage.getItem('jwt'); }
  get company() { return localStorage.getItem('company') ?? ''; }
  get loggedIn() { return !!this.token; }

  login(email: string, password: string) {
    return this.http.post<{ token: string; companyName: string }>('/api/auth/login', { email, password }).pipe(tap(r => this.store(r)));
  }
  register(email: string, password: string, companyName: string) {
    return this.http.post<{ token: string; companyName: string }>('/api/auth/register', { email, password, companyName }).pipe(tap(r => this.store(r)));
  }
  logout() {
    localStorage.removeItem('jwt');
    localStorage.removeItem('company');
    this.injector.get(BrandService).clear();
    this.router.navigate(['/login']);
  }
  private store(r: { token: string; companyName: string }) {
    localStorage.setItem('jwt', r.token);
    localStorage.setItem('company', r.companyName);
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const r = auth.token ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } }) : req;
  return next(r).pipe(catchError(e => {
    if (e.status === 401 && !req.url.includes('/api/auth')) auth.logout();   // expired token
    return throwError(() => e);
  }));
};

export const authGuard: CanActivateFn = () => inject(AuthService).loggedIn || inject(Router).createUrlTree(['/login']);
