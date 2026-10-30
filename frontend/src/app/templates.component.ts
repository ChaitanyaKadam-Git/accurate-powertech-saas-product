import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api, DocTemplate, LINES, TemplateSection, VISIBLE_DOC_TYPES, docTypeOf, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const KINDS = [
  { key: 'TEXT', label: 'Text' },
  { key: 'BOM', label: 'Item table' },
  { key: 'OFFER', label: 'Totals' },
];
const STYLES = [
  { key: 'BULLETS', label: 'Bullet points' },
  { key: 'PARAGRAPH', label: 'Paragraphs' },
  { key: 'TABLE', label: 'Table (Topic | Details)' },
];

/** Masters > Templates: several templates per document type (quotation, proforma, invoice) and business line. */
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, IconComponent],
  template: `
    <div class="page-h">
      <div><h1>Templates</h1><p class="muted">Starting sections and PDF options for quotations, several per line.</p></div>
      @if (auth.can('TEMPLATE_DATA', 'CREATE')) { <button (click)="open()"><app-icon name="plus" [size]="18" /> Add template</button> }
    </div>

    <div class="card">
      <div class="row" style="margin-bottom:12px; justify-content:flex-start; gap:12px">
        @if (types.length > 1) {
          <div class="seg">
            @for (t of types; track t.key) { <button type="button" [class.on]="typeF === t.key" (click)="typeF = t.key">{{ t.label }} ({{ count(t.key) }})</button> }
          </div>
        }
        <div class="seg">
          <button type="button" [class.on]="lineF === ''" (click)="lineF = ''">All lines</button>
          @for (l of lines; track l.key) { <button type="button" [class.on]="lineF === l.key" (click)="lineF = l.key">{{ l.label }}</button> }
        </div>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="scroll"><table>
        <thead><tr><th>Template</th>@if (types.length > 1) { <th>Type</th> }<th>Line</th><th>PDF title</th><th>Cover</th><th>Letter</th><th>Prices</th><th class="r">Sections</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>
          @for (t of shown; track t.id) {
            <tr>
              <td><b>{{ t.name }}</b> @if (t.isDefault) { <span class="badge ok">Default</span> }</td>
              @if (types.length > 1) { <td>{{ typeOf(t.docType).label }}</td> }
              <td><span class="badge">{{ lineOf(t.businessLine).label }}</span></td>
              <td>{{ t.pdfTitle || 'Built-in' }}</td>
              <td>{{ t.showCover ? 'Yes' : 'No' }}</td>
              <td>{{ t.showLetter ? 'Yes' : 'No' }}</td>
              <td>{{ t.showPrices ? 'Yes' : 'No' }}</td>
              <td class="r">{{ t.sections.length }}</td>
              <td><span class="badge" [class.ok]="t.active" [class.off]="!t.active">{{ t.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="actions">
                <button class="link" (click)="open(t)">{{ auth.can('TEMPLATE_DATA', 'EDIT') ? 'Edit' : 'View' }}</button>
                @if (auth.can('TEMPLATE_DATA', 'CREATE')) { <button class="link" (click)="duplicate(t)">Duplicate</button> }
                @if (auth.can('TEMPLATE_DATA', 'EDIT') && !t.isDefault && t.active) { <button class="link" (click)="makeDefault(t)">Make default</button> }
                @if (auth.can('TEMPLATE_DATA', 'DELETE')) { <button class="link danger" (click)="remove(t)">Delete</button> }
              </td>
            </tr>
          }
        </tbody>
      </table></div>
      @if (loaded && !shown.length) { <p class="muted">No templates here yet.</p> }
    </div>

    @if (f) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><div><h3>{{ f.id ? (editable ? 'Edit template' : f.name) : 'Add template' }}</h3>@if (f.updatedAt) { <span class="muted small">Last updated {{ f.updatedAt | date: 'dd MMM yyyy, HH:mm' }}</span> }</div>
          <button class="icon-btn" (click)="close()" aria-label="Close"><app-icon name="x" /></button></div>
        <fieldset class="plain" [disabled]="!editable">
          <div class="row" style="justify-content:flex-start; gap:12px; margin-bottom:12px">
            <div [hidden]="types.length < 2"><span class="lbl">Document type</span><div class="seg">@for (t of types; track t.key) { <button type="button" [class.on]="f.docType === t.key" (click)="f.docType = t.key">{{ t.label }}</button> }</div></div>
            <div><span class="lbl">Business line</span><div class="seg">@for (l of lines; track l.key) { <button type="button" [class.on]="f.businessLine === l.key" (click)="f.businessLine = l.key">{{ l.label }}</button> }</div></div>
          </div>
          <div class="grid">
            <label class="wide"><span>Template name <span class="req">*</span></span><input [(ngModel)]="f.name" placeholder="e.g. Genset - hospital offer, Solar - residential" /></label>
            <label>Title on the PDF<input [(ngModel)]="f.pdfTitle" placeholder="Empty = built-in title" /></label>
            <label>Description<input [(ngModel)]="f.description" /></label>
          </div>
          <div class="row" style="justify-content:flex-start; gap:18px; margin:12px 0">
            @if (f.docType === 'QUOTATION') {
              <label class="check"><input type="checkbox" [(ngModel)]="f.showCover" /> Cover page</label>
              <label class="check"><input type="checkbox" [(ngModel)]="f.showLetter" /> Cover letter</label>
            }
            <label class="check"><input type="checkbox" [(ngModel)]="f.showPrices" /> Item prices in the item table</label>
            <label class="check"><input type="checkbox" [(ngModel)]="f.isDefault" /> Default for {{ typeOf(f.docType).label.toLowerCase() }} / {{ lineOf(f.businessLine).label }}</label>
            <label class="check"><input type="checkbox" [(ngModel)]="f.active" /> Active</label>
          </div>

          <div class="row" style="margin-top:8px"><h3>Sections ({{ f.sections.length }})</h3>
            @if (editable) { <button type="button" (click)="addSection()"><app-icon name="plus" [size]="16" /> Add section</button> }</div>
          <p class="hint">The order here is the order in the PDF. "Item table" prints the document's items, "Totals" the amount with GST. You can use {{ '{capacity}' }}, {{ '{location}' }} and {{ '{client}' }} in text.</p>
          @for (s of f.sections; track s; let i = $index) {
            <div class="sec">
              <div class="row">
                <span class="badge">{{ i + 1 }}</span>
                <input class="grow" [(ngModel)]="s.title" placeholder="Section title" />
                <select [(ngModel)]="s.kind" style="width:auto">@for (k of kinds; track k.key) { <option [value]="k.key">{{ k.label }}</option> }</select>
                <label class="check"><input type="checkbox" [(ngModel)]="s.visible" /> Show</label>
                @if (editable) {
                  <button type="button" class="link" [disabled]="i === 0" (click)="move(i, -1)">Up</button>
                  <button type="button" class="link" [disabled]="i === f.sections.length - 1" (click)="move(i, 1)">Down</button>
                  <button type="button" class="link danger" (click)="f.sections.splice(i, 1)">Remove</button>
                }
              </div>
              @if (s.kind === 'TEXT') {
                <select [(ngModel)]="s.style" style="max-width:260px">@for (st of styles; track st.key) { <option [value]="st.key">{{ st.label }}</option> }</select>
                <textarea rows="4" [(ngModel)]="s.content"></textarea>
              }
            </div>
          }
        </fieldset>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px">
          @if (editable) { <button [disabled]="busy" (click)="save()">{{ f.id ? 'Save changes' : 'Add template' }}</button> }
          <button class="ghost" (click)="close()">{{ editable ? 'Cancel' : 'Close' }}</button>
        </div>
      </div></div>
    }
  `,
})
export class TemplatesComponent implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  types = VISIBLE_DOC_TYPES; lines = LINES; kinds = KINDS; styles = STYLES;
  lineOf = lineOf; typeOf = docTypeOf;
  rows: DocTemplate[] = [];
  typeF = 'QUOTATION'; lineF = '';
  error = ''; loaded = false;
  f: DocTemplate | null = null; formError = ''; busy = false;

  get editable() { return this.f?.id ? this.auth.can('TEMPLATE_DATA', 'EDIT') : this.auth.can('TEMPLATE_DATA', 'CREATE'); }
  get shown() { return this.rows.filter(t => t.docType === this.typeF && (!this.lineF || t.businessLine === this.lineF)); }
  count(type: string) { return this.rows.filter(t => t.docType === type).length; }

  ngOnInit() { this.load(); }
  load() {
    this.api.templates().subscribe({ next: r => { this.rows = r; this.loaded = true; }, error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load templates'; } });
  }

  open(t?: DocTemplate) {
    this.formError = '';
    this.f = t
      ? { ...t, pdfTitle: t.pdfTitle ?? '', description: t.description ?? '', sections: t.sections.map(s => ({ ...s, content: s.content ?? '' })) }
      : { docType: this.typeF, businessLine: this.lineF || 'GENSET', name: '', isDefault: false, active: true, pdfTitle: '', showCover: this.typeF === 'QUOTATION',
          showLetter: this.typeF === 'QUOTATION', showPrices: true, description: '',
          sections: [{ kind: 'BOM', title: 'Items', content: '', style: 'BULLETS', visible: true }, { kind: 'OFFER', title: 'Commercial offer', content: '', style: 'BULLETS', visible: true }] };
  }
  close() { if (this.editable && this.f && !confirm('Close without saving?')) return; this.f = null; }

  addSection() { this.f?.sections.push({ kind: 'TEXT', title: 'New section', content: '', style: 'BULLETS', visible: true }); }
  move(i: number, dir: number) {
    const a = this.f!.sections; const [x] = a.splice(i, 1); a.splice(i + dir, 0, x);
  }

  save() {
    if (!this.f) return;
    if (!this.f.name.trim()) { this.formError = 'Enter the template name.'; return; }
    this.busy = true; this.formError = '';
    this.api.saveTemplate(this.f).subscribe({
      next: t => { this.busy = false; this.f = null; this.typeF = t.docType; this.load(); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save the template'; },
    });
  }

  duplicate(t: DocTemplate) {
    this.api.cloneTemplate(t.id!).subscribe({ next: c => { this.load(); this.open(c); }, error: e => (this.error = e.error?.message || 'Could not duplicate') });
  }
  makeDefault(t: DocTemplate) {
    this.api.saveTemplate({ ...t, isDefault: true }).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not change the default') });
  }
  remove(t: DocTemplate) {
    if (!confirm(`Delete the template "${t.name}"? Documents already made keep their own text.`)) return;
    this.api.deleteTemplate(t.id!).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not delete') });
  }
}
