import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, Signed } from './auth';
import { BrandService } from './brand';
import { IconComponent } from './icon.component';

@Component({
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="auth">
      <section class="auth-hero">
        <div class="logo-box">
          @if (logoOk) { <img src="logo.png" alt="Company logo" (error)="logoOk = false" /> } @else { <span>QS</span> }
        </div>

        <div class="hero-art">
          <svg viewBox="0 0 440 280" width="440" height="280" fill="none" aria-hidden="true">
            <circle cx="372" cy="62" r="34" fill="#f5b942" />
            <g stroke="#f5b942" stroke-width="4" stroke-linecap="round">
              <path d="M372 8v10M372 106v10M318 62h10M416 62h10M334 24l7 7M403 93l7 7M410 24l-7 7M341 93l-7 7" />
            </g>
            <rect x="40" y="34" width="250" height="214" rx="16" fill="#fff" />
            <rect x="64" y="58" width="130" height="14" rx="7" fill="#2563eb" />
            <rect x="64" y="84" width="180" height="8" rx="4" fill="#cbd5e1" />
            <rect x="64" y="100" width="150" height="8" rx="4" fill="#cbd5e1" />
            <rect x="64" y="128" width="202" height="22" rx="6" fill="#e0e9fb" />
            <rect x="64" y="156" width="202" height="22" rx="6" fill="#f1f5f9" />
            <rect x="64" y="184" width="202" height="22" rx="6" fill="#e0e9fb" />
            <rect x="190" y="216" width="76" height="20" rx="10" fill="#16a34a" />
            <g transform="translate(250 150) skewX(-12)">
              <rect width="170" height="96" rx="6" fill="#1e3a8a" />
              <path d="M0 32h170M0 64h170M42 0v96M85 0v96M128 0v96" stroke="#7aa2ff" stroke-width="2" />
            </g>
          </svg>
        </div>

        <div>
          <h1>Create professional solar proposals in minutes.</h1>
          <p>Reusable standard text, photos in every section, automatic GST totals and a ready-to-send PDF.</p>
        </div>
        <small>&copy; {{ year }} Quote SaaS. All rights reserved.</small>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <h2>{{ registering ? 'Create your company account' : 'Sign in' }}</h2>
          <p class="muted">
            {{ registering ? 'You will be the administrator who creates users and decides what each of them can do.'
              : 'Administrators and employees sign in here. You will be taken to the right place automatically.' }}
          </p>

          @if (registering) {
            <label>Company name
              <span class="field"><app-icon name="building" /><input placeholder="Your company name" [(ngModel)]="company" /></span>
            </label>
          }
          <label>Email <span class="req">*</span>
            <span class="field"><app-icon name="mail" /><input type="email" placeholder="you@company.com" [(ngModel)]="email" /></span>
          </label>
          <label>Password <span class="req">*</span>
            <span class="field">
              <app-icon name="lock" />
              <input [type]="show ? 'text' : 'password'" [placeholder]="registering ? 'At least 8 characters' : 'Your password'"
                     [(ngModel)]="password" (keyup.enter)="submit()" />
              <button type="button" class="eye" (click)="show = !show" aria-label="Show or hide password"><app-icon name="eye" /></button>
            </span>
          </label>

          @if (error) { <p class="error">{{ error }}</p> }
          <div><button class="big" [disabled]="busy" (click)="submit()">{{ registering ? 'Create account' : 'Sign in' }}</button></div>
          <button class="link" (click)="registering = !registering; error = ''">
            {{ registering ? 'I already have an account' : 'Create a new company account' }}
          </button>
          @if (!registering) { <p class="hint">Employees: your administrator creates your account and gives you a temporary password.</p> }
        </div>
      </section>
    </div>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private brand = inject(BrandService);
  registering = false; show = false; logoOk = true; busy = false;
  email = ''; password = ''; company = ''; error = '';
  year = new Date().getFullYear();

  submit() {
    this.error = '';
    if (!this.email.trim() || !this.password) { this.error = 'Enter your email and password.'; return; }
    this.busy = true;
    const req = this.registering ? this.auth.register(this.email, this.password, this.company) : this.auth.login(this.email, this.password);
    req.subscribe({
      next: (r: Signed) => {
        this.busy = false;
        if (r.mustChangePassword) { this.router.navigate(['/set-password']); return; }   // first sign-in: choose your own password
        this.brand.refresh();
        this.router.navigate([r.portal === 'ADMIN' ? '/admin' : '/']);
      },
      error: e => { this.busy = false; this.error = e.error?.message || 'Could not sign in. Check your details and try again.'; },
    });
  }
}
