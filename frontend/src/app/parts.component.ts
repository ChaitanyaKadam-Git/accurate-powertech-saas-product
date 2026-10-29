import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api, LINES, Part, PartImportReport, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';
import { ImagePickerComponent } from './image-picker.component';

const EMPTY: Part = { name: '', category: '', businessLine: 'BOTH', specification: '', make: '', uom: 'Nos.', unitPrice: 0, hsnCode: '', imageKey: null, active: true };

/** Masters > Parts: every part once with its price. Products use these parts; a price change applies to all of them. */
@Component({
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe, IconComponent, ImagePickerComponent],
  template: `
    <div class="page-h">
      <div><h1>Parts</h1><p class="muted">Every component once, with its current price.</p></div>
      @if (auth.can('PRODUCTS', 'CREATE')) {
        <div>
          <button class="ghost" (click)="openImport()"><app-icon name="upload" [size]="18" /> Import Excel</button>
          <button class="ghost" (click)="bulk = true; bulkText = ''; bulkResult = ''" title="Paste rows copied from Excel">Paste rows</button>
          <button (click)="open()"><app-icon name="plus" [size]="18" /> Add part</button>
        </div>
      }
    </div>

    <div class="card">
      <div class="seg" style="margin-bottom:14px">
        <button type="button" [class.on]="lineF === ''" (click)="lineF = ''">All ({{ rows.length }})</button>
        @for (l of lines; track l.key) { <button type="button" [class.on]="lineF === l.key" (click)="lineF = l.key">{{ l.label }} ({{ count(l.key) }})</button> }
      </div>
      <div class="toolbar">
        <label class="grow-field">Search<input placeholder="Part, make, specification, HSN" [(ngModel)]="q" /></label>
        <label>Category<select [(ngModel)]="catF"><option value="">All categories</option>@for (c of categories; track c) { <option [value]="c">{{ c }}</option> }</select></label>
        <label>Status<select [(ngModel)]="statusF"><option value="">All</option><option value="active">Active</option><option value="inactive">Inactive</option></select></label>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="scroll"><table>
        <thead><tr><th>Part</th><th>Category</th><th>Make</th><th>Line</th><th>Specification</th><th>UOM</th><th class="r">Unit price</th><th>HSN</th><th class="r">Used in</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>
          @for (p of shown; track p.id) {
            <tr>
              <td><b>{{ p.name }}</b></td>
              <td>{{ p.category || '-' }}</td>
              <td>{{ p.make || '-' }}</td>
              <td><span class="badge">{{ lineLabel(p.businessLine) }}</span></td>
              <td><span class="clip" [title]="p.specification || ''">{{ p.specification || '-' }}</span></td>
              <td>{{ p.uom }}</td>
              <td class="r"><b>&#8377;{{ p.unitPrice | number: '1.2-2' }}</b></td>
              <td>{{ p.hsnCode || '-' }}</td>
              <td class="r">{{ p.usedIn ? p.usedIn + ' product' + (p.usedIn > 1 ? 's' : '') : '-' }}</td>
              <td><span class="badge" [class.ok]="p.active" [class.off]="!p.active">{{ p.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="actions">
                <button class="link" (click)="open(p)">{{ auth.can('PRODUCTS', 'EDIT') ? 'Edit' : 'View' }}</button>
                @if (auth.can('PRODUCTS', 'DELETE')) { <button class="link danger" (click)="remove(p)">Delete</button> }
              </td>
            </tr>
          }
        </tbody>
      </table></div>
      @if (loaded && shown.length === 0) { <p class="muted">{{ rows.length ? 'No parts match.' : 'No parts yet. Add them one by one or paste a price list from Excel.' }}</p> }
    </div>

    @if (f) {
      <div class="modal-bg"><div class="modal">
        <div class="modal-h"><div><h3>{{ f.id ? (editable ? 'Edit part' : f.name) : 'Add part' }}</h3>@if (f.updatedAt) { <span class="muted small">Last updated {{ f.updatedAt | date: 'dd MMM yyyy, HH:mm' }}</span> }</div>
          <button class="icon-btn" (click)="f = null" aria-label="Close"><app-icon name="x" /></button></div>
        @if (f.usedIn) { <p class="notice">Used in {{ f.usedIn }} product(s). A change here applies to all of them; statements already made keep their own copy.</p> }
        <fieldset class="plain" [disabled]="!editable">
          <span class="lbl">Used for</span>
          <div class="seg" style="margin-bottom:12px">
            <button type="button" [class.on]="f.businessLine === 'BOTH'" (click)="f.businessLine = 'BOTH'">Both</button>
            @for (l of lines; track l.key) { <button type="button" [class.on]="f.businessLine === l.key" (click)="f.businessLine = l.key">{{ l.label }}</button> }
          </div>
          <div class="grid">
            <label class="wide"><span>Part name <span class="req">*</span></span><input [(ngModel)]="f.name" placeholder="e.g. AMF panel 125 kVA, Mono PERC module 540 Wp" /></label>
            <label>Category<input [(ngModel)]="f.category" list="part-categories" placeholder="Engine, Panel, Module, Cable ..." /></label>
            <label>Make<input [(ngModel)]="f.make" /></label>
            <label>UOM<input [(ngModel)]="f.uom" /></label>
            <label>Unit price (&#8377;, excl. GST)<input type="number" min="0" [(ngModel)]="f.unitPrice" /></label>
            <label>HSN / SAC code<input [(ngModel)]="f.hsnCode" inputmode="numeric" maxlength="8" /></label>
            <label class="wide">Specification<textarea rows="2" [(ngModel)]="f.specification"></textarea></label>
            <label class="check"><input type="checkbox" [(ngModel)]="f.active" /> Active (can be added to products and statements)</label>
          </div>
          <datalist id="part-categories">@for (c of categories; track c) { <option [value]="c"></option> }</datalist>
          <image-picker label="Photo (optional)" [key]="f.imageKey" (keyChange)="f.imageKey = $event" />
        </fieldset>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px">
          @if (editable) { <button [disabled]="busy" (click)="save()">{{ f.id ? 'Save changes' : 'Add part' }}</button> }
          <button class="ghost" (click)="f = null">{{ editable ? 'Cancel' : 'Close' }}</button>
        </div>
      </div></div>
    }

    @if (imp) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><h3>Import parts from Excel</h3><button class="icon-btn" (click)="closeImport()" aria-label="Close"><app-icon name="x" /></button></div>
        @if (!report) {
          <p>1. Download the template. 2. Fill one row per part (up to 2000). 3. Upload it. You see a row-by-row check before anything is saved.</p>
          <p>
            <button class="ghost" (click)="template('xlsx')"><app-icon name="download" [size]="18" /> Excel template</button>
            <button class="ghost" (click)="template('csv')"><app-icon name="download" [size]="18" /> CSV template</button>
          </p>
          <label>Filled template (.xlsx or .csv)<input type="file" accept=".xlsx,.csv" (change)="pickFile($event)" /></label>
          @if (auth.can('PRODUCTS', 'EDIT')) {
            <label class="check" style="margin-top:10px"><input type="checkbox" [(ngModel)]="updateExisting" /> Update existing parts (same part name + make): new price and details replace the old ones</label>
          }
          @if (impError) { <p class="error">{{ impError }}</p> }
          <div style="margin-top:12px"><button [disabled]="!file || busy" (click)="runImport(false)">Check file</button></div>
        } @else {
          <div class="stats" style="margin-bottom:12px">
            <div class="stat blue"><div><small>Rows</small><b>{{ report.total }}</b></div></div>
            <div class="stat green"><div><small>{{ report.committed ? 'Created' : 'New parts' }}</small><b>{{ report.committed ? report.created : report.newCount }}</b></div></div>
            <div class="stat violet"><div><small>{{ report.committed ? 'Updated' : 'Updates' }}</small><b>{{ report.committed ? report.updated : report.updateCount }}</b></div></div>
            <div class="stat amber"><div><small>With errors</small><b>{{ report.invalid }}</b></div></div>
          </div>
          @if (report.committed) { <p class="ok">Import finished: {{ report.created }} part(s) created, {{ report.updated }} updated, {{ report.invalid }} skipped.</p> }
          @else if (report.invalid > 0) { <p class="error">{{ report.invalid }} row(s) have problems and will be skipped. Fix them in your file and upload again, or import only the valid rows.</p> }
          <div class="scroll" style="max-height:360px;overflow:auto"><table>
            <thead><tr><th>Row</th><th>Part</th><th>Category</th><th>Line</th><th class="r">Unit price</th><th>Result</th></tr></thead>
            <tbody>
              @for (r of report.rows; track r.row) {
                <tr [class.row-err]="r.status === 'ERROR'" [class.row-ok]="r.status !== 'ERROR'">
                  <td>{{ r.row }}</td>
                  <td><b>{{ r.name || '-' }}</b>@if (r.make) { <br /><span class="muted small">{{ r.make }}</span> }</td>
                  <td>{{ r.category || '-' }}</td>
                  <td>{{ lineLabel(r.line) }}</td>
                  <td class="r nowrap">&#8377;{{ r.unitPrice | number: '1.2-2' }}</td>
                  <td class="small">@if (r.status === 'ERROR') { <span class="error">{{ r.errors.join('; ') }}</span> }
                    @else { <span class="badge" [class.ok]="r.status === 'NEW'" [class.warn]="r.status === 'UPDATE'">{{ r.status === 'NEW' ? (report.committed ? 'Created' : 'New') : (report.committed ? 'Updated' : 'Update') }}</span> }</td>
                </tr>
              }
            </tbody>
          </table></div>
          <div style="margin-top:12px">
            @if (!report.committed) {
              <button [disabled]="report.newCount + report.updateCount === 0 || busy" (click)="runImport(true)">Import {{ report.newCount + report.updateCount }} part(s)</button>
              <button class="ghost" (click)="report = null; file = null">Choose another file</button>
            } @else { <button (click)="closeImport()">Close</button> }
          </div>
          @if (impError) { <p class="error">{{ impError }}</p> }
        }
      </div></div>
    }

    @if (bulk) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><h3>Paste parts from Excel</h3><button class="icon-btn" (click)="bulk = false" aria-label="Close"><app-icon name="x" /></button></div>
        <p>Copy rows with these columns, in this order: <b>part name, category, make, specification, UOM, unit price, HSN, line</b> (Solar, Genset or Both; empty = Both).</p>
        <textarea rows="8" [(ngModel)]="bulkText" placeholder="AMF panel 125 kVA&#9;Panel&#9;Accurate&#9;Auto mains failure&#9;Nos.&#9;95000&#9;8537&#9;Genset"></textarea>
        @if (bulkResult) { <p [class.error]="bulkFailed" [class.ok]="!bulkFailed" style="white-space:pre-line">{{ bulkResult }}</p> }
        <div style="margin-top:12px"><button [disabled]="busy || !bulkText.trim()" (click)="importBulk()">Add parts</button><button class="ghost" (click)="bulk = false">Close</button></div>
      </div></div>
    }
  `,
})
export class PartsComponent implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  lines = LINES;
  rows: Part[] = [];
  q = ''; lineF = ''; catF = ''; statusF = ''; error = ''; loaded = false;
  f: Part | null = null;
  formError = ''; busy = false;
  bulk = false; bulkText = ''; bulkResult = ''; bulkFailed = false;
  imp = false; file: File | null = null; report: PartImportReport | null = null; impError = ''; updateExisting = false;

  get editable() { return this.f?.id ? this.auth.can('PRODUCTS', 'EDIT') : this.auth.can('PRODUCTS', 'CREATE'); }
  get categories() { return [...new Set(this.rows.map(r => r.category).filter((c): c is string => !!c))].sort((a, b) => a.localeCompare(b)); }
  lineLabel(l: string) { return l === 'BOTH' ? 'Both' : lineOf(l).label; }
  count(line: string) { return this.rows.filter(p => p.businessLine === line || p.businessLine === 'BOTH').length; }
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter(p =>
      (!this.lineF || p.businessLine === this.lineF || p.businessLine === 'BOTH') &&
      (!this.catF || p.category === this.catF) &&
      (!this.statusF || (this.statusF === 'active') === p.active) &&
      (!q || [p.name, p.make, p.specification, p.hsnCode, p.category].some(v => (v || '').toLowerCase().includes(q))));
  }

  ngOnInit() { this.load(); }
  load() {
    this.api.parts().subscribe({
      next: r => { this.rows = r; this.loaded = true; },
      error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load parts'; },
    });
  }

  open(p?: Part) {
    this.formError = '';
    this.f = p ? { ...EMPTY, ...Object.fromEntries(Object.entries(p).map(([k, v]) => [k, v ?? (k === 'imageKey' ? null : '')])) } as Part : { ...EMPTY, businessLine: this.lineF || 'BOTH' };
  }

  save() {
    if (!this.f) return;
    if (!this.f.name.trim()) { this.formError = 'Enter the part name.'; return; }
    this.busy = true; this.formError = '';
    this.api.savePart(this.f).subscribe({
      next: () => { this.busy = false; this.f = null; this.load(); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save the part'; },
    });
  }

  remove(p: Part) {
    if (!confirm(`Delete the part "${p.name}"?`)) return;
    this.api.deletePart(p.id!).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not delete') });
  }

  openImport() { this.imp = true; this.file = null; this.report = null; this.impError = ''; this.updateExisting = false; }
  closeImport() { this.imp = false; this.load(); }
  pickFile(ev: Event) { this.file = (ev.target as HTMLInputElement).files?.[0] ?? null; this.impError = ''; }
  template(fmt: string) { this.api.saveBlob('/api/parts/import/template', `parts-import-template.${fmt}`, { format: fmt }); }
  runImport(commit: boolean) {
    if (!this.file) return;
    if (commit && !confirm(`Import ${this.report!.newCount} new part(s)` + (this.report!.updateCount ? ` and update ${this.report!.updateCount} existing part(s)` : '') + '?')) return;
    this.busy = true; this.impError = '';
    this.api.importParts(this.file, commit, this.updateExisting).subscribe({
      next: r => { this.busy = false; this.report = r; },
      error: e => { this.busy = false; this.impError = e.error?.message || 'Could not read the file'; },
    });
  }

  /** One row per line, tab separated (copied from Excel). Saved one by one; problems are listed per row. */
  async importBulk() {
    const toLine = (v: string) => { const s = (v || '').trim().toLowerCase(); return s.startsWith('sol') ? 'SOLAR' : s.startsWith('gen') ? 'GENSET' : 'BOTH'; };
    const rows = this.bulkText.split('\n').map(l => l.split('\t')).filter(c => c[0]?.trim());
    this.busy = true; this.bulkResult = '';
    let ok = 0; const errs: string[] = [];
    for (const [i, c] of rows.entries()) {
      const p: Part = { ...EMPTY, name: c[0].trim(), category: c[1]?.trim() ?? '', make: c[2]?.trim() ?? '', specification: c[3]?.trim() ?? '',
        uom: c[4]?.trim() || 'Nos.', unitPrice: Number((c[5] ?? '').replace(/,/g, '')) || 0, hsnCode: c[6]?.trim() ?? '', businessLine: toLine(c[7]) };
      try { await new Promise((res, rej) => this.api.savePart(p).subscribe({ next: res, error: rej })); ok++; }
      catch (e: any) { errs.push(`Row ${i + 1} (${p.name}): ${e.error?.message || 'could not be saved'}`); }
    }
    this.busy = false;
    this.bulkFailed = errs.length > 0;
    this.bulkResult = `${ok} part(s) added.` + (errs.length ? `\n${errs.length} row(s) skipped:\n` + errs.join('\n') : '');
    if (!errs.length) this.bulkText = '';
    this.load();
  }
}
