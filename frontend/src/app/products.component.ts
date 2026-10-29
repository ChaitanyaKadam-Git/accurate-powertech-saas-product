import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, LINES, Part, Product, ProductPart, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const emptyPart = (): ProductPart => ({ partId: null, qty: 1 });

/** Masters > Products: Genset / Solar setups and their parts. A statement can pull in every part of a product at once. */
@Component({
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe, IconComponent, RouterLink],
  template: `
    <div class="page-h">
      <div><h1>Products</h1><p class="muted">Genset and solar setups built from the <a routerLink="/parts">Parts</a> master. In a statement, "Add a product" copies all its parts into the items.</p></div>
      @if (auth.can('PRODUCTS', 'CREATE')) { <button (click)="open()"><app-icon name="plus" [size]="18" /> Add product</button> }
    </div>

    <div class="card">
      <div class="seg" style="margin-bottom:14px">
        <button type="button" [class.on]="lineF === ''" (click)="lineF = ''">All ({{ rows.length }})</button>
        @for (l of lines; track l.key) { <button type="button" [class.on]="lineF === l.key" (click)="lineF = l.key">{{ l.label }} ({{ count(l.key) }})</button> }
      </div>
      <div class="toolbar">
        <label class="grow-field">Search<input placeholder="Product, code, part, make" [(ngModel)]="q" /></label>
        <label>Status<select [(ngModel)]="statusF"><option value="">All</option><option value="active">Active</option><option value="inactive">Inactive</option></select></label>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="scroll"><table>
        <thead><tr><th>Product</th><th>Line</th><th>Capacity</th><th>Parts</th><th class="r">Total (excl. GST)</th><th>Status</th><th></th></tr></thead>
        <tbody>
          @for (p of shown; track p.id) {
            <tr>
              <td class="w-main"><b>{{ p.name }}</b>@if (p.code) { <br /><span class="muted small">{{ p.code }}</span> }</td>
              <td><span class="badge">{{ lineOf(p.businessLine).label }}</span></td>
              <td class="nowrap">{{ p.capacity != null ? (p.capacity | number: '1.0-2') + ' ' + lineOf(p.businessLine).unit : '-' }}</td>
              <td>{{ p.parts.length }}</td>
              <td class="r nowrap">&#8377;{{ p.total | number: '1.2-2' }}</td>
              <td><span class="badge" [class.ok]="p.active" [class.off]="!p.active">{{ p.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="actions stack-actions">
                <button class="link" (click)="open(p)">{{ auth.can('PRODUCTS', 'EDIT') ? 'Edit' : 'View' }}</button>
                @if (auth.can('PRODUCTS', 'CREATE')) { <button class="link" (click)="duplicate(p)">Duplicate</button> }
                @if (auth.can('PRODUCTS', 'DELETE')) { <button class="link danger" (click)="remove(p)">Delete</button> }
              </td>
            </tr>
          }
        </tbody>
      </table></div>
      @if (loaded && shown.length === 0) { <p class="muted">{{ rows.length ? 'No products match.' : 'No products yet. Click "Add product" to create a Genset or Solar setup with its parts.' }}</p> }
    </div>

    @if (f) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><div><h3>{{ f.id ? (editable ? 'Edit product' : f.name) : 'Add product' }}</h3>@if (f.updatedAt) { <span class="muted small">Last updated {{ f.updatedAt | date: 'dd MMM yyyy, HH:mm' }}</span> }</div>
          <button class="icon-btn" (click)="close()" aria-label="Close"><app-icon name="x" /></button></div>
        <fieldset class="plain" [disabled]="!editable">
          <span class="lbl">Business line</span>
          <div class="seg" style="margin-bottom:12px">
            @for (l of lines; track l.key) { <button type="button" [class.on]="f.businessLine === l.key" (click)="f.businessLine = l.key">{{ l.label }}</button> }
          </div>
          <div class="grid">
            <label class="wide"><span>Product name <span class="req">*</span></span><input [(ngModel)]="f.name" [placeholder]="f.businessLine === 'GENSET' ? 'e.g. 125 kVA KOEL iGreen DG set' : 'e.g. 30 kWp rooftop on-grid system'" /></label>
            <label>Model / code<input [(ngModel)]="f.code" /></label>
            <label>Capacity ({{ lineOf(f.businessLine).unit }})<input type="number" min="0" [(ngModel)]="f.capacity" /></label>
            <label class="wide">Description<textarea rows="2" [(ngModel)]="f.description"></textarea></label>
            <label class="check"><input type="checkbox" [(ngModel)]="f.active" /> Active (offered in new statements)</label>
          </div>

          <div class="row" style="margin-top:16px"><h3>Parts ({{ named }})</h3>
            @if (editable) { <button type="button" (click)="f.parts.push(newPart())"><app-icon name="plus" [size]="16" /> Add part</button> }
          </div>
          <p class="hint">Pick parts from the <a routerLink="/parts" (click)="f = null">Parts</a> master ({{ choices.length }} available for {{ lineOf(f.businessLine).label }}). Prices come from the master.</p>
          <div class="scroll"><table class="items">
            <thead><tr><th>#</th><th>Part</th><th>Make</th><th>UOM</th><th>Qty</th><th class="r">Unit price</th><th class="r">Amount</th><th></th></tr></thead>
            <tbody>
              @for (x of f.parts; track x; let i = $index) {
                <tr>
                  <td>{{ i + 1 }}</td>
                  <td>
                    <select [(ngModel)]="x.partId" [ngModelOptions]="{ standalone: true }" class="part-pick">
                      <option [ngValue]="null">Select a part</option>
                      @for (c of choicesFor(x); track c.id) { <option [ngValue]="c.id">{{ c.name }}{{ c.make ? ' (' + c.make + ')' : '' }}{{ c.active ? '' : ' - inactive' }}</option> }
                    </select>
                    @if (master(x)?.specification) { <div class="muted small">{{ master(x)?.specification }}</div> }
                  </td>
                  <td>{{ master(x)?.make || '-' }}</td>
                  <td>{{ master(x)?.uom || '-' }}</td>
                  <td><input class="s" type="number" min="0" [(ngModel)]="x.qty" [ngModelOptions]="{ standalone: true }" /></td>
                  <td class="r nowrap">{{ (master(x)?.unitPrice ?? 0) | number: '1.2-2' }}</td>
                  <td class="r nowrap">{{ (x.qty || 0) * (master(x)?.unitPrice ?? 0) | number: '1.2-2' }}</td>
                  <td>@if (editable) { <button type="button" class="link danger" (click)="f.parts.splice(i, 1)">Remove</button> }</td>
                </tr>
              }
            </tbody>
          </table></div>
          <div class="totals"><div class="grand">Total (excl. GST) <b>&#8377;{{ total | number: '1.2-2' }}</b></div></div>
        </fieldset>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px">
          @if (editable) { <button [disabled]="busy" (click)="save()">{{ f.id ? 'Save changes' : 'Add product' }}</button> }
          <button class="ghost" (click)="close()">{{ editable ? 'Cancel' : 'Close' }}</button>
        </div>
      </div></div>
    }
  `,
})
export class ProductsComponent implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  lines = LINES;
  lineOf = lineOf;
  rows: Product[] = [];
  q = ''; lineF = ''; statusF = ''; error = ''; loaded = false;
  f: Product | null = null;
  formError = ''; busy = false;
  allParts: Part[] = [];

  get editable() { return this.f?.id ? this.auth.can('PRODUCTS', 'EDIT') : this.auth.can('PRODUCTS', 'CREATE'); }
  get named() { return this.f?.parts.filter(x => x.partId).length ?? 0; }
  get total() { return (this.f?.parts ?? []).reduce((s, x) => s + (Number(x.qty) || 0) * (Number(this.master(x)?.unitPrice) || 0), 0); }
  /** Active master parts this product's line may use. */
  get choices() { const l = this.f?.businessLine; return this.allParts.filter(p => p.active && (p.businessLine === 'BOTH' || p.businessLine === l)); }
  /** The choices plus the part already on this row (so an inactive or other-line part still shows). */
  choicesFor(x: ProductPart) { const cur = this.master(x); return cur && !this.choices.includes(cur) ? [cur, ...this.choices] : this.choices; }
  master(x: ProductPart) { return this.allParts.find(p => p.id === x.partId); }
  count(line: string) { return this.rows.filter(p => p.businessLine === line).length; }
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter(p =>
      (!this.lineF || p.businessLine === this.lineF) &&
      (!this.statusF || (this.statusF === 'active') === p.active) &&
      (!q || [p.name, p.code, p.description, ...p.parts.flatMap(x => [x.name ?? '', x.make ?? ''])].some(v => (v || '').toLowerCase().includes(q))));
  }

  ngOnInit() { this.load(); this.api.parts().subscribe(p => (this.allParts = p)); }
  load() {
    this.api.products().subscribe({
      next: r => { this.rows = r; this.loaded = true; },
      error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load products'; },
    });
  }

  newPart() { return emptyPart(); }

  open(p?: Product) {
    this.formError = '';
    this.f = p
      ? { ...p, code: p.code ?? '', description: p.description ?? '', parts: p.parts.map(x => ({ partId: x.partId, qty: x.qty })) }
      : { businessLine: this.lineF || 'GENSET', name: '', code: '', capacity: null, description: '', active: true, parts: [emptyPart()] };
  }

  close() {
    if (this.editable && this.f && !confirm('Close without saving?')) return;
    this.f = null;
  }

  save() {
    if (!this.f) return;
    if (!this.f.name.trim()) { this.formError = 'Enter the product name.'; return; }
    this.busy = true; this.formError = '';
    this.api.saveProduct({ ...this.f, parts: this.f.parts.filter(x => x.partId) }).subscribe({
      next: () => { this.busy = false; this.f = null; this.load(); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save the product'; },
    });
  }

  duplicate(p: Product) {
    this.api.cloneProduct(p.id!).subscribe({ next: c => { this.load(); this.open(c); }, error: e => (this.error = e.error?.message || 'Could not duplicate') });
  }

  remove(p: Product) {
    if (!confirm(`Delete the product "${p.name}" and its ${p.parts.length} part(s)? Statements already made are not changed.`)) return;
    this.api.deleteProduct(p.id!).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not delete') });
  }
}
