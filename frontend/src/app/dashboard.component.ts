import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api, Summary } from './api';
import { AuthService } from './auth';
import { BrandService } from './brand';
import { IconComponent } from './icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe, IconComponent],
  template: `
    <div class="hero card">
      <div>
        <h1>{{ greeting }}, {{ brand.name() }}</h1>
        <p class="muted">Turn a client enquiry into a finished proposal PDF.</p>
        <span class="chip"><app-icon name="calendar" [size]="16" /> {{ today | date: 'EEEE, d MMM y' }}</span>
      </div>
      @if (auth.can('QUOTATIONS', 'CREATE')) { <a class="btn big" routerLink="/quote/new"><app-icon name="plus" [size]="18" /> New statement</a> }
    </div>
    @if (!auth.can('QUOTATIONS', 'VIEW')) {
      <div class="card"><h3>Welcome, {{ auth.userName() }}</h3><p class="muted">Your account does not have access to quotations yet. Ask your administrator to grant the rights you need.</p></div>
    } @else {

    <div class="stats">
      <div class="stat blue"><span class="ic"><app-icon name="file" /></span><div><small>Total statements</small><b>{{ rows.length }}</b></div></div>
      <div class="stat violet"><span class="ic"><app-icon name="calendar" /></span><div><small>This month</small><b>{{ thisMonth }}</b></div></div>
      <div class="stat green"><span class="ic"><app-icon name="cash" /></span><div><small>Total value (incl. GST)</small><b>&#8377;{{ total | number: '1.0-0' }}</b></div></div>
      <div class="stat amber"><span class="ic"><app-icon name="trend" /></span><div><small>Average statement</small><b>&#8377;{{ avg | number: '1.0-0' }}</b></div></div>
    </div>

    <div class="card">
      <div class="card-h">
        <div><h3>Recent statements</h3><p>Your latest proposals.</p></div>
        <a routerLink="/statements">View all</a>
      </div>
      @if (recent.length === 0) { <p class="muted">No statements yet. Click "New statement" to create your first one.</p> }
      @else {
        <div class="scroll"><table>
          <thead><tr><th>Ref</th><th>Client</th><th>Location</th><th>Date</th><th class="r">Total</th><th></th></tr></thead>
          <tbody>
            @for (r of recent; track r.id) {
              <tr>
                <td>{{ r.refNo }}</td><td>{{ r.clientName }}</td><td>{{ r.projectLocation }}</td>
                <td>{{ r.quoteDate | date: 'dd MMM yyyy' }}</td><td class="r">&#8377;{{ r.total | number: '1.2-2' }}</td>
                <td class="actions"><a [routerLink]="['/quote', r.id]">Edit</a>@if (auth.can('PDF', 'DOWNLOAD')) { <button class="link" (click)="api.download(r.id, r.refNo)">PDF</button> }</td>
              </tr>
            }
          </tbody>
        </table></div>
      }
    </div>
    }
  `,
})
export class DashboardComponent implements OnInit {
  api = inject(Api);
  auth = inject(AuthService);
  brand = inject(BrandService);
  rows: Summary[] = [];
  today = new Date();

  ngOnInit() { if (this.auth.can('QUOTATIONS', 'VIEW')) this.api.list().subscribe(r => (this.rows = r)); }

  get greeting() { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
  get total() { return this.rows.reduce((s, r) => s + (r.total || 0), 0); }
  get avg() { return this.rows.length ? this.total / this.rows.length : 0; }
  get thisMonth() { const d = new Date(), m = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; return this.rows.filter(r => (r.quoteDate || '').startsWith(m)).length; }
  get recent() { return this.rows.slice(0, 6); }
}
