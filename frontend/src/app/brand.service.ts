import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from './api';

/** Company name, logo and signed-in email shown in the top bar. */
@Injectable({ providedIn: 'root' })
export class Brand {
  private api = inject(Api);
  company = signal('');
  email = signal('');
  logoUrl = signal('');
  loaded = false;
  initials = computed(() => ((this.company() || this.email() || '?').trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('')).toUpperCase());

  load() {
    this.loaded = true;
    this.api.company().subscribe(c => {
      this.company.set(c.companyName || '');
      if (!c.logoKey) { this.logoUrl.set(''); return; }
      this.api.image(c.logoKey).subscribe({ next: b => this.logoUrl.set(URL.createObjectURL(b)), error: () => this.logoUrl.set('') });
    });
    this.api.account().subscribe(a => this.email.set(a.email));
  }

  clear() { this.loaded = false; this.company.set(''); this.email.set(''); this.logoUrl.set(''); }
}
