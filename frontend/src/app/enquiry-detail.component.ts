import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api, Enquiry, EnquiryDetail, LINES, SOURCES, STAGES, lineOf, stageOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const KIND: Record<string, string> = { CREATED: 'Created', STAGE: 'Stage', CALL: 'Call', VISIT: 'Visit', NOTE: 'Note', QUOTATION: 'Statement' };

/** One enquiry: where it is in the customer flow, its details and dates, its statements and its timeline. */
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, IconComponent],
  template: `
    <p><a routerLink="/enquiries">&larr; All enquiries</a></p>
    @if (d; as d) {
      <div class="page-h">
        <div>
          <h1>{{ d.enquiry.refNo }} <span class="badge">{{ lineOf(d.enquiry.businessLine).label }}</span>
            <span class="badge" [class.ok]="won" [class.off]="d.enquiry.stage === 'LOST'">{{ d.stageLabel }}</span></h1>
          <p class="muted">{{ d.enquiry.title }} · <a [routerLink]="['/customers', d.enquiry.customerId]">{{ d.customer?.name }}</a>@if (d.customer?.city) { , {{ d.customer?.city }} } · Owner: {{ d.ownerName || '-' }}</p>
        </div>
        <div>
          @if (canEdit && next) { <button (click)="openMove(next.key)"><app-icon name="check" [size]="18" /> {{ next.label }}</button> }
          @if (auth.can('QUOTATIONS', 'CREATE') && d.enquiry.stage !== 'LOST') {
            <a class="btn ghost" [routerLink]="['/quote/new']" [queryParams]="{ enquiry: d.enquiry.id }"><app-icon name="file" [size]="18" /> New statement</a>
          }
        </div>
      </div>

      <div class="card">
        <ol class="stepper">
          @for (s of flow; track s.key; let i = $index) {
            <li [class.done]="i < pos" [class.cur]="i === pos" [class.lost]="d.enquiry.stage === 'LOST'">
              <span class="dot">{{ i < pos ? '✓' : i + 1 }}</span><span class="small">{{ s.label }}</span>
              @if (stageDate(s.key); as dt) { <span class="muted small">{{ dt | date: 'dd MMM' }}</span> }
            </li>
          }
        </ol>
        @if (d.enquiry.stage === 'LOST') { <p class="error">Lost on {{ d.enquiry.stageDate | date: 'dd MMM yyyy' }}: {{ d.enquiry.lostReason }}</p> }
        @if (canEdit) {
          <div class="row" style="margin-top:10px; justify-content:flex-start">
            <label>Move to stage
              <select #mv (change)="mv.value && openMove(mv.value); mv.value = ''">
                <option value="">Choose a stage</option>
                @for (s of stages; track s.key) { @if (s.key !== d.enquiry.stage) { <option [value]="s.key">{{ s.label }}</option> } }
              </select>
            </label>
          </div>
        }
      </div>

      <div class="grid2">
        <div class="card">
          <div class="card-h"><div><h3>Details</h3></div>@if (canEdit) { <button class="ghost" (click)="openEdit()">Edit</button> }</div>
          <div class="kv">
            <div><small>Customer</small><b><a [routerLink]="['/customers', d.enquiry.customerId]">{{ d.customer?.name }}</a></b></div>
            <div><small>Contact</small><b>{{ contactLine || '-' }}</b></div>
            <div><small>Capacity</small><b>{{ d.enquiry.capacity != null ? (d.enquiry.capacity | number: '1.0-2') + ' ' + lineOf(d.enquiry.businessLine).unit : '-' }}</b></div>
            <div><small>Source</small><b>{{ d.enquiry.source || '-' }}</b></div>
            <div><small>Expected value</small><b>{{ d.enquiry.expectedValue != null ? '₹' + (d.enquiry.expectedValue | number: '1.0-0') : '-' }}</b></div>
            <div><small>Next follow-up</small><b [class.error]="followUpDue">{{ d.enquiry.nextFollowUp ? (d.enquiry.nextFollowUp | date: 'dd MMM yyyy') : '-' }}</b></div>
            <div><small>Order</small><b>{{ d.enquiry.orderNo || '-' }}{{ d.enquiry.orderValue != null ? ' · ₹' + (d.enquiry.orderValue | number: '1.0-0') : '' }}</b></div>
            <div><small>Order date</small><b>{{ (d.enquiry.orderDate | date: 'dd MMM yyyy') || '-' }}</b></div>
            <div><small>Delivered</small><b>{{ (d.enquiry.deliveryDate | date: 'dd MMM yyyy') || '-' }}</b></div>
            <div><small>Installed</small><b>{{ (d.enquiry.installationDate | date: 'dd MMM yyyy') || '-' }}</b></div>
            <div><small>Commissioned</small><b>{{ (d.enquiry.commissioningDate | date: 'dd MMM yyyy') || '-' }}</b></div>
            <div><small>Warranty until</small><b>{{ (d.enquiry.warrantyUntil | date: 'dd MMM yyyy') || '-' }}</b></div>
            <div><small>AMC until</small><b>{{ (d.enquiry.amcUntil | date: 'dd MMM yyyy') || '-' }}</b></div>
          </div>
          @if (d.enquiry.notes) { <p class="small" style="white-space:pre-line;margin-top:12px">{{ d.enquiry.notes }}</p> }
        </div>

        <div class="card">
          <div class="card-h"><div><h3>Statements ({{ d.quotations.length }})</h3><p>Quotations made for this enquiry.</p></div></div>
          @for (q of d.quotations; track q.id) {
            <div class="row" style="padding:6px 0;border-bottom:1px solid var(--line)">
              <a [routerLink]="['/quote', q.id]"><b>{{ q.refNo }}</b></a><span class="muted small">{{ q.quoteDate | date: 'dd MMM yyyy' }}</span><b>&#8377;{{ q.total | number: '1.0-0' }}</b>
            </div>
          }
          @if (!d.quotations.length) { <p class="muted">No statement yet. "New statement" fills in this customer and links it here.</p> }
        </div>
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Timeline</h3><p>Calls, visits, notes and every stage change.</p></div></div>
        @if (canEdit) {
          <div class="note-form">
            <div class="seg">
              @for (k of ['CALL', 'VISIT', 'NOTE']; track k) { <button type="button" [class.on]="note.kind === k" (click)="note.kind = k">{{ kindLabel(k) }}</button> }
            </div>
            <textarea rows="2" [(ngModel)]="note.text" placeholder="What happened? e.g. Spoke to purchase manager, wants revised price by Friday"></textarea>
            <div class="toolbar" style="margin:0">
              <label>Date<input type="date" [(ngModel)]="note.date" [max]="todayStr" /></label>
              <label>Next follow-up<input type="date" [(ngModel)]="note.nextFollowUp" /></label>
              <button [disabled]="busy || !note.text.trim()" (click)="addNote()">Add to timeline</button>
            </div>
          </div>
        }
        <ul class="timeline">
          @for (ev of d.events; track ev.id) {
            <li [class]="'k-' + ev.kind.toLowerCase()">
              <span class="badge">{{ kindLabel(ev.kind) }}</span>
              <div><div>{{ ev.text }}</div><span class="muted small">{{ ev.eventDate | date: 'dd MMM yyyy' }} · {{ ev.userName }}</span></div>
            </li>
          }
        </ul>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      @if (auth.can('CUSTOMERS', 'DELETE')) { <button class="link danger" (click)="remove()">Delete enquiry</button> }
    }

    @if (mv) {
      <div class="modal-bg"><div class="modal">
        <div class="modal-h"><h3>Move to: {{ stageLabel(mv.stage) }}</h3><button class="icon-btn" (click)="mv = null" aria-label="Close"><app-icon name="x" /></button></div>
        <div class="grid">
          <label>Date<input type="date" [(ngModel)]="mv.date" [max]="todayStr" /></label>
          @if (mv.stage === 'ORDER') {
            <label>Order / PO number<input [(ngModel)]="mv.orderNo" /></label>
            <label>Order value (&#8377;)<input type="number" min="0" [(ngModel)]="mv.orderValue" /></label>
          }
          @if (mv.stage === 'COMMISSIONED') {
            <label>Warranty until<input type="date" [(ngModel)]="mv.warrantyUntil" /></label>
            <label>AMC / maintenance contract until<input type="date" [(ngModel)]="mv.amcUntil" /></label>
          }
          @if (mv.stage === 'LOST') { <label class="wide"><span>Why was it lost? <span class="req">*</span></span><input [(ngModel)]="mv.lostReason" placeholder="Price, competitor, project dropped, no response ..." /></label> }
          <label class="wide">Note<textarea rows="2" [(ngModel)]="mv.note"></textarea></label>
        </div>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px"><button [disabled]="busy" (click)="move()">Save</button><button class="ghost" (click)="mv = null">Cancel</button></div>
      </div></div>
    }

    @if (f) {
      <div class="modal-bg"><div class="modal">
        <div class="modal-h"><h3>Edit enquiry</h3><button class="icon-btn" (click)="f = null" aria-label="Close"><app-icon name="x" /></button></div>
        <span class="lbl">Business line</span>
        <div class="seg" style="margin-bottom:12px">@for (l of lines; track l.key) { <button type="button" [class.on]="f.businessLine === l.key" (click)="f.businessLine = l.key">{{ l.label }}</button> }</div>
        <div class="grid">
          <label class="wide"><span>Requirement <span class="req">*</span></span><input [(ngModel)]="f.title" /></label>
          <label>Capacity ({{ lineOf(f.businessLine).unit }})<input type="number" min="0" [(ngModel)]="f.capacity" /></label>
          <label>Source<select [(ngModel)]="f.source"><option value="">Select</option>@for (s of sources; track s) { <option [value]="s">{{ s }}</option> }</select></label>
          <label>Expected value (&#8377;)<input type="number" min="0" [(ngModel)]="f.expectedValue" /></label>
          <label>Next follow-up<input type="date" [(ngModel)]="f.nextFollowUp" /></label>
          @if (owners.length > 1) { <label>Owner<select [(ngModel)]="f.ownerId">@for (o of owners; track o.id) { <option [ngValue]="o.id">{{ o.name }}</option> }</select></label> }
          <label>Order / PO number<input [(ngModel)]="f.orderNo" /></label>
          <label>Order value (&#8377;)<input type="number" min="0" [(ngModel)]="f.orderValue" /></label>
          <label>Order date<input type="date" [(ngModel)]="f.orderDate" /></label>
          <label>Delivered<input type="date" [(ngModel)]="f.deliveryDate" /></label>
          <label>Installed<input type="date" [(ngModel)]="f.installationDate" /></label>
          <label>Commissioned<input type="date" [(ngModel)]="f.commissioningDate" /></label>
          <label>Warranty until<input type="date" [(ngModel)]="f.warrantyUntil" /></label>
          <label>AMC until<input type="date" [(ngModel)]="f.amcUntil" /></label>
          <label class="wide">Notes<textarea rows="3" [(ngModel)]="f.notes"></textarea></label>
        </div>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px"><button [disabled]="busy" (click)="saveEdit()">Save changes</button><button class="ghost" (click)="f = null">Cancel</button></div>
      </div></div>
    }
  `,
})
export class EnquiryDetailComponent implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);
  lines = LINES; stages = STAGES; sources = SOURCES; lineOf = lineOf;
  flow = STAGES.filter(s => s.key !== 'LOST');
  todayStr = today();
  d: EnquiryDetail | null = null;
  owners: { id: number; name: string }[] = [];
  note = { kind: 'CALL', text: '', date: today(), nextFollowUp: '' as string | null };
  mv: { stage: string; date: string; note: string; lostReason: string; orderNo: string; orderValue: number | null; warrantyUntil: string | null; amcUntil: string | null } | null = null;
  f: Enquiry | null = null;
  error = ''; formError = ''; busy = false;

  get canEdit() { return this.auth.can('CUSTOMERS', 'EDIT'); }
  get pos() { return this.d ? (this.d.enquiry.stage === 'LOST' ? -1 : this.flow.findIndex(s => s.key === this.d!.enquiry.stage)) : 0; }
  get next() { const p = this.pos; return p >= 0 && p < this.flow.length - 1 ? this.flow[p + 1] : null; }
  get won() { return !!this.d && this.d.enquiry.stage !== 'LOST' && this.pos >= 4; }
  get followUpDue() { return !!this.d?.enquiry.nextFollowUp && this.d.enquiry.nextFollowUp <= today(); }
  get contactLine() { const c = this.d?.customer; return [c?.contactPerson, c?.phone].filter(Boolean).join(' · '); }
  kindLabel(k: string) { return KIND[k] ?? k; }
  stageLabel(k: string) { return stageOf(k).label; }
  /** Date a stage was reached, from the timeline (latest move into it). */
  stageDate(key: string) { return this.d?.events.find(e => e.kind === 'STAGE' && e.stage === key)?.eventDate ?? (key === 'NEW' ? this.d?.events.find(e => e.kind === 'CREATED')?.eventDate : null); }

  ngOnInit() {
    this.route.paramMap.subscribe(p => this.load(+p.get('id')!));
    this.api.enquiryOwners().subscribe(o => (this.owners = o));
  }
  load(id: number) { this.api.enquiry(id).subscribe({ next: d => (this.d = d), error: e => (this.error = e.error?.message || 'Could not load the enquiry') }); }

  openMove(stage: string) {
    const e = this.d!.enquiry;
    this.formError = '';
    this.mv = { stage, date: today(), note: '', lostReason: '', orderNo: e.orderNo ?? '', orderValue: e.orderValue ?? e.expectedValue ?? null,
      warrantyUntil: e.warrantyUntil ?? null, amcUntil: e.amcUntil ?? null };
  }
  move() {
    if (!this.mv || !this.d) return;
    if (this.mv.stage === 'LOST' && !this.mv.lostReason.trim()) { this.formError = 'Enter why the enquiry was lost.'; return; }
    this.busy = true; this.formError = '';
    this.api.moveEnquiry(this.d.enquiry.id!, this.mv).subscribe({
      next: d => { this.busy = false; this.d = d; this.mv = null; },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not change the stage'; },
    });
  }

  addNote() {
    if (!this.d) return;
    this.busy = true; this.error = '';
    this.api.addEnquiryNote(this.d.enquiry.id!, { ...this.note, nextFollowUp: this.note.nextFollowUp || null }).subscribe({
      next: d => { this.busy = false; this.d = d; this.note = { kind: this.note.kind, text: '', date: today(), nextFollowUp: '' }; },
      error: e => { this.busy = false; this.error = e.error?.message || 'Could not add the note'; },
    });
  }

  openEdit() {
    const e = this.d!.enquiry;
    this.formError = '';
    this.f = { ...e, source: e.source ?? '', notes: e.notes ?? '' };
  }
  saveEdit() {
    if (!this.f) return;
    if (!this.f.title.trim()) { this.formError = 'Describe the requirement.'; return; }
    this.busy = true; this.formError = '';
    const clean = Object.fromEntries(Object.entries(this.f).map(([k, v]) => [k, v === '' && k !== 'title' && k !== 'notes' && k !== 'source' ? null : v])) as unknown as Enquiry;
    this.api.saveEnquiry(clean).subscribe({
      next: d => { this.busy = false; this.d = d; this.f = null; },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save'; },
    });
  }

  remove() {
    if (!this.d || !confirm(`Delete enquiry ${this.d.enquiry.refNo}? Its statements are kept but no longer linked.`)) return;
    this.api.deleteEnquiry(this.d.enquiry.id!).subscribe({ next: () => this.router.navigate(['/enquiries']), error: e => (this.error = e.error?.message || 'Could not delete') });
  }
}
