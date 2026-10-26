import { Injectable, inject, signal } from '@angular/core';
import { Api } from './api';

/** Company name, logo and sign-in email shown in the top bar. */
@Injectable({ providedIn: 'root' })
export class BrandService {
  private api = inject(Api);
  name = signal(localStorage.getItem('company') ?? '');
  logo = signal('');
  email = signal('');

  refresh() {
    this.api.company().subscribe(c => {
      this.name.set(c.companyName);
      localStorage.setItem('company', c.companyName);
      if (c.logoKey) this.api.image(c.logoKey).subscribe(b => this.logo.set(URL.createObjectURL(b)));
      else this.logo.set('');
    });
    this.api.account().subscribe(a => this.email.set(a.email));
  }

  clear() { this.logo.set(''); this.email.set(''); }
}
