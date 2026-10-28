import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from './api';
import { AuthService } from './auth';
import { BrandService } from './brand';
import { IconComponent } from './icon.component';

/** First sign-in (or after an admin reset): the temporary password must be replaced by one the person chooses. */
@Component({
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="auth-panel" style="min-height:100vh">
      <div class="auth-card">
        <h2>Set your password</h2>
        <p class="muted">Welcome{{ auth.userName() ? ', ' + auth.userName() : '' }}. Replace the temporary password you were given with one only you know.</p>
        <label>Temporary password
          <span class="field"><app-icon name="key" /><input type="password" [(ngModel)]="current" autocomplete="current-password" /></span>
        </label>
        <label>New password (min 8 characters)
          <span class="field"><app-icon name="lock" /><input type="password" [(ngModel)]="next" autocomplete="new-password" /></span>
        </label>
        <label>Confirm new password
          <span class="field"><app-icon name="lock" /><input type="password" [(ngModel)]="again" (keyup.enter)="submit()" autocomplete="new-password" /></span>
        </label>
        @if (error) { <p class="error">{{ error }}</p> }
        <div><button class="big" [disabled]="busy" (click)="submit()">Save password and continue</button></div>
        <button class="link" (click)="auth.logout()">Sign out</button>
      </div>
    </div>
  `,
})
export class SetPasswordComponent {
  private api = inject(Api);
  private router = inject(Router);
  private brand = inject(BrandService);
  auth = inject(AuthService);
  current = ''; next = ''; again = ''; error = ''; busy = false;

  submit() {
    this.error = '';
    if (this.next.length < 8) { this.error = 'The new password must be at least 8 characters.'; return; }
    if (this.next !== this.again) { this.error = 'The two new passwords do not match.'; return; }
    this.busy = true;
    this.api.changePassword(this.current, this.next).subscribe({
      next: () => {
        this.auth.passwordChanged();
        this.auth.me().subscribe(() => {   // reload rights now that the account is unlocked
          this.brand.refresh();
          this.router.navigate([this.auth.isAdmin() ? '/admin' : '/']);
        });
      },
      error: e => { this.busy = false; this.error = e.error?.message || 'Could not change the password. Try again.'; },
    });
  }
}
