import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, Customer } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const EMPTY: Customer = { code: '', name: '', contactPerson: '', phone: '', email: '', gstin: '', address: '', city: '', notes: '' };

/** Masters > Customers: the clients a company quotes for. Picked in the statement form to fill the client details. */
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, IconComponent, RouterLink],
  template: `
    <div class="page-h">
      <div><h1>Customers</h1><p class="muted">Your clients, each tracked by a customer code.</p></div>
      @if (auth.can('CUSTOMERS', 'CREATE')) { <button (click)="open()"><app-icon name="plus" [size]="18" /> Add customer</button> }
    </div>

    <div class="card">
      <div class="toolbar">
        <label class="grow-field">Search<input placeholder="Code, name, contact, phone, email, city, GSTIN" [(ngModel)]="q" /></label>
        <label>City<select [(ngModel)]="cityF"><option value="">All cities</option>@for (c of cities; track c) { <option [value]="c">{{ c }}</option> }</select></label>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="scroll"><table>
        <thead><tr><th>Code</th><th>Customer</th><th>Contact person</th><th>Phone</th><th>Email</th><th>GSTIN</th><th>City</th><th>Address</th><th>Actions</th></tr></thead>
        <tbody>
          @for (c of shown; track c.id) {
            <tr>
              <td><a class="code" [routerLink]="['/customers', c.id]">{{ c.code }}</a></td>
              <td><a [routerLink]="['/customers', c.id]">{{ c.name }}</a></td>
              <td>{{ c.contactPerson || '-' }}</td>
              <td>{{ c.phone || '-' }}</td>
              <td>{{ c.email || '-' }}</td>
              <td>{{ c.gstin || '-' }}</td>
              <td>{{ c.city || '-' }}</td>
              <td><span class="clip" [title]="c.address || ''">{{ c.address || '-' }}</span></td>
              <td class="actions">
                <button class="link" (click)="open(c)">{{ auth.can('CUSTOMERS', 'EDIT') ? 'Edit' : 'View' }}</button>
                @if (auth.can('CUSTOMERS', 'DELETE')) { <button class="link danger" (click)="remove(c)">Delete</button> }
              </td>
            </tr>
          }
        </tbody>
      </table></div>
      @if (loaded && shown.length === 0) { <p class="muted">{{ rows.length ? 'No customers match.' : 'No customers yet. Click "Add customer" to create your first one.' }}</p> }
      @if (rows.length) { <p class="hint">{{ shown.length }} of {{ rows.length }} customer(s)</p> }
    </div>

    @if (f) {
      <div class="modal-bg" (click)="f = null"><div class="modal" (click)="$event.stopPropagation()">
        <div class="modal-h"><div><h3>{{ f.id ? (editable ? 'Edit customer' : f.name) : 'Add customer' }}</h3>@if (f.updatedAt) { <span class="muted small">Last updated {{ f.updatedAt | date: 'dd MMM yyyy, HH:mm' }}</span> }</div><button class="icon-btn" (click)="f = null" aria-label="Close"><app-icon name="x" /></button></div>
        <fieldset class="plain" [disabled]="!editable">
          <div class="grid">
            <label>Customer code<input [(ngModel)]="f.code" maxlength="20" placeholder="Automatic (CUST-0001 ...)" (input)="f.code = (f.code || '').toUpperCase()" /></label>
            <label class="wide"><span>Customer name <span class="req">*</span></span><input [(ngModel)]="f.name" placeholder="Company or person" /></label>
            <label>Contact person<input [(ngModel)]="f.contactPerson" /></label>
            <label>Phone<input [(ngModel)]="f.phone" inputmode="numeric" maxlength="10" placeholder="10-digit number" (input)="f.phone = digits(f.phone)" /></label>
            <label>Email<input type="email" [(ngModel)]="f.email" /></label>
            <label>GSTIN<input [(ngModel)]="f.gstin" maxlength="15" placeholder="27ABCDE1234F1Z5" (input)="f.gstin = (f.gstin || '').toUpperCase()" /></label>
            <label class="wide">Address<textarea rows="2" [(ngModel)]="f.address"></textarea></label>
            <label>City / project location<input [(ngModel)]="f.city" /></label>
            <label class="wide">Notes<textarea rows="2" [(ngModel)]="f.notes"></textarea></label>
          </div>
        </fieldset>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px">
          @if (editable) { <button [disabled]="busy" (click)="save()">{{ f.id ? 'Save changes' : 'Add customer' }}</button> }
          <button class="ghost" (click)="f = null">{{ editable ? 'Cancel' : 'Close' }}</button>
        </div>
      </div></div>
    }
  `,
})
export class CustomersComponent implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  rows: Customer[] = [];
  q = ''; cityF = ''; error = ''; loaded = false;
  f: Customer | null = null;
  formError = ''; busy = false;

  get editable() { return this.f?.id ? this.auth.can('CUSTOMERS', 'EDIT') : this.auth.can('CUSTOMERS', 'CREATE'); }
  get cities() { return [...new Set(this.rows.map(r => r.city).filter((c): c is string => !!c))].sort((a, b) => a.localeCompare(b)); }
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter(c =>
      (!q || [c.code, c.name, c.contactPerson, c.phone, c.email, c.city, c.gstin, c.address].some(v => (v || '').toLowerCase().includes(q))) &&
      (!this.cityF || c.city === this.cityF));
  }

  ngOnInit() { this.load(); }
  load() {
    this.api.customers().subscribe({
      next: r => { this.rows = r; this.loaded = true; },
      error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load customers'; },
    });
  }

  digits(v: string) { return (v || '').replace(/\D/g, '').slice(0, 10); }

  open(c?: Customer) {
    this.formError = '';
    this.f = c ? { ...EMPTY, ...Object.fromEntries(Object.entries(c).map(([k, v]) => [k, v ?? ''])) } as Customer : { ...EMPTY };
  }

  save() {
    if (!this.f) return;
    if (!this.f.name.trim()) { this.formError = 'Enter the customer name.'; return; }
    this.busy = true; this.formError = '';
    this.api.saveCustomer(this.f).subscribe({
      next: () => { this.busy = false; this.f = null; this.load(); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save the customer'; },
    });
  }

  remove(c: Customer) {
    if (!confirm(`Delete the customer "${c.name}"? Statements already made for them are not changed.`)) return;
    this.api.deleteCustomer(c.id!).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not delete') });
  }
}
