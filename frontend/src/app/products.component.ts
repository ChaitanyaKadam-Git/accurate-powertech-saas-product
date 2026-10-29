import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api, LINES, Product, ProductPart, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const emptyPart = (): ProductPart => ({ name: '', specification: '', make: '', uom: 'Nos.', qty: 1, unitPrice: 0, imageKey: null });

/** Masters > Products: Genset / Solar setups and their parts. A statement can pull in every part of a product at once. */
@Component({
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe, IconComponent],
  template: `
    <div class="page-h">
      <div><h1>Products</h1><p class="muted">Genset and solar setups with their parts. In a statement, "Add product" copies all parts into the items.</p></div>
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
          @if (editable) {
            <details style="margin:6px 0 10px"><summary>Paste parts from Excel</summary>
              <p class="hint">Columns: part, specification, make, UOM, qty, unit price.</p>
              <textarea rows="4" [(ngModel)]="paste"></textarea>
              <button type="button" class="ghost" (click)="importPaste()">Add pasted rows</button>
            </details>
          }
          <div class="scroll"><table class="items">
            <thead><tr><th>#</th><th>Part</th><th>Specification</th><th>Make</th><th>UOM</th><th>Qty</th><th>Unit price</th><th class="r">Amount</th><th></th></tr></thead>
            <tbody>
              @for (x of f.parts; track x; let i = $index) {
                <tr>
                  <td>{{ i + 1 }}</td>
                  <td><input [(ngModel)]="x.name" placeholder="Part name" /></td>
                  <td><textarea rows="1" [(ngModel)]="x.specification"></textarea></td>
                  <td><input [(ngModel)]="x.make" /></td>
                  <td><input class="s" [(ngModel)]="x.uom" /></td>
                  <td><input class="s" type="number" min="0" [(ngModel)]="x.qty" /></td>
                  <td><input class="m" type="number" min="0" [(ngModel)]="x.unitPrice" /></td>
                  <td class="r nowrap">{{ (x.qty || 0) * (x.unitPrice || 0) | number: '1.2-2' }}</td>
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
  formError = ''; busy = false; paste = '';

  get editable() { return this.f?.id ? this.auth.can('PRODUCTS', 'EDIT') : this.auth.can('PRODUCTS', 'CREATE'); }
  get named() { return this.f?.parts.filter(x => x.name.trim()).length ?? 0; }
  get total() { return (this.f?.parts ?? []).reduce((s, x) => s + (Number(x.qty) || 0) * (Number(x.unitPrice) || 0), 0); }
  count(line: string) { return this.rows.filter(p => p.businessLine === line).length; }
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter(p =>
      (!this.lineF || p.businessLine === this.lineF) &&
      (!this.statusF || (this.statusF === 'active') === p.active) &&
      (!q || [p.name, p.code, p.description, ...p.parts.flatMap(x => [x.name, x.make])].some(v => (v || '').toLowerCase().includes(q))));
  }

  ngOnInit() { this.load(); }
  load() {
    this.api.products().subscribe({
      next: r => { this.rows = r; this.loaded = true; },
      error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load products'; },
    });
  }

  newPart() { return emptyPart(); }

  open(p?: Product) {
    this.formError = ''; this.paste = '';
    this.f = p
      ? { ...p, code: p.code ?? '', description: p.description ?? '', parts: p.parts.map(x => ({ ...x, specification: x.specification ?? '', make: x.make ?? '', uom: x.uom ?? 'Nos.' })) }
      : { businessLine: this.lineF || 'GENSET', name: '', code: '', capacity: null, description: '', active: true, parts: [emptyPart()] };
  }

  close() {
    if (this.editable && this.f && !confirm('Close without saving?')) return;
    this.f = null;
  }

  importPaste() {
    if (!this.f) return;
    const rows = this.paste.split('\n').map(l => l.split('\t')).filter(c => c[0]?.trim());
    if (this.f.parts.length === 1 && !this.f.parts[0].name.trim()) this.f.parts = [];
    rows.forEach(c => this.f!.parts.push({
      name: c[0].trim(), specification: c[1]?.trim() ?? '', make: c[2]?.trim() ?? '', uom: c[3]?.trim() || 'Nos.',
      qty: Number(c[4]) || 1, unitPrice: Number((c[5] ?? '').replace(/,/g, '')) || 0, imageKey: null,
    }));
    this.paste = '';
  }

  save() {
    if (!this.f) return;
    if (!this.f.name.trim()) { this.formError = 'Enter the product name.'; return; }
    this.busy = true; this.formError = '';
    this.api.saveProduct(this.f).subscribe({
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
