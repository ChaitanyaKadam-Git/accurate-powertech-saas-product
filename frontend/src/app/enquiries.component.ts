import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api, Customer, Enquiry, EnquiryRow, EnquiryStats, LINES, SOURCES, STAGES, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

/** Customer flow: every enquiry from first contact to commissioning, as a board (one column per stage) or a list. */
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, IconComponent],
  template: `
    <div class="page-h">
      <div><h1>Enquiries</h1><p class="muted">Every enquiry from first contact to commissioning.</p></div>
      @if (auth.can('ENQUIRIES', 'CREATE')) { <button (click)="openNew()"><app-icon name="plus" [size]="18" /> New enquiry</button> }
    </div>

    @if (stats) {
      <div class="stats">
        <div class="stat blue"><span class="ic"><app-icon name="funnel" /></span><div><small>Open enquiries</small><b>{{ openCount }}</b></div></div>
        <div class="stat amber"><span class="ic"><app-icon name="calendar" /></span><div><small>Follow-ups due</small><b>{{ stats.followUps.length }}</b></div></div>
        <div class="stat violet"><span class="ic"><app-icon name="cash" /></span><div><small>Open pipeline value</small><b>&#8377;{{ stats.openValue | number: '1.0-0' }}</b></div></div>
        <div class="stat green"><span class="ic"><app-icon name="check" /></span><div><small>Won this year ({{ stats.wonThisYear }})</small><b>&#8377;{{ stats.wonValueThisYear | number: '1.0-0' }}</b></div></div>
      </div>
    }

    <div class="card">
      <div class="row" style="margin-bottom:12px">
        <div class="seg">
          <button type="button" [class.on]="view === 'board'" (click)="setView('board')">Board</button>
          <button type="button" [class.on]="view === 'list'" (click)="setView('list')">List</button>
        </div>
      </div>
      <div class="toolbar">
        <label class="grow-field">Search<input placeholder="Ref, customer code or name, requirement, city" [(ngModel)]="q" /></label>
        <label>Business line<select [(ngModel)]="lineF"><option value="">All lines</option>@for (l of lines; track l.key) { <option [value]="l.key">{{ l.label }}</option> }</select></label>
        @if (owners.length > 1) {
          <label>Owner<select [(ngModel)]="ownerF"><option [ngValue]="null">Everyone</option>@for (o of owners; track o.id) { <option [ngValue]="o.id">{{ o.name }}</option> }</select></label>
        }
        @if (view === 'list') {
          <label>Stage<select [(ngModel)]="stageF"><option value="">All stages</option>@for (s of stages; track s.key) { <option [value]="s.key">{{ s.label }}</option> }</select></label>
        }
        <label class="check"><input type="checkbox" [(ngModel)]="dueOnly" /> Follow-up due</label>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }

      @if (view === 'board') {
        <div class="board">
          @for (s of stages; track s.key) {
            <div class="col" [class.lost]="s.key === 'LOST'">
              <div class="col-h"><b>{{ s.label }}</b><span class="badge">{{ inStage(s.key).length }}</span></div>
              @for (e of inStage(s.key); track e.id) {
                <a class="ecard" [routerLink]="['/enquiries', e.id]">
                  <small class="muted">{{ e.refNo }} · {{ lineOf(e.businessLine).label }}</small>
                  <b>{{ e.customerName }}</b>@if (e.customerCode) { <span class="code" style="align-self:flex-start">{{ e.customerCode }}</span> }
                  <span class="small">{{ e.title }}</span>
                  <span class="small muted">{{ e.ownerName || '-' }}@if (e.expectedValue || e.orderValue) { · &#8377;{{ (e.orderValue || e.expectedValue) | number: '1.0-0' }} }</span>
                  @if (e.nextFollowUp && open(e)) { <span class="badge" [class.off]="due(e)">Follow-up {{ e.nextFollowUp | date: 'dd MMM' }}</span> }
                </a>
              }
            </div>
          }
        </div>
      } @else {
        <div class="scroll"><table>
          <thead><tr><th>Ref</th><th>Line</th><th>Customer code</th><th>Customer</th><th>City</th><th>Requirement</th><th>Stage</th><th>Since</th><th>Owner</th><th class="r">Value</th><th>Follow-up</th><th>Actions</th></tr></thead>
          <tbody>
            @for (e of shown; track e.id) {
              <tr>
                <td><a [routerLink]="['/enquiries', e.id]">{{ e.refNo }}</a></td>
                <td><span class="badge">{{ lineOf(e.businessLine).label }}</span></td>
                <td>@if (e.customerCode) { <a class="code" [routerLink]="['/customers', e.customerId]">{{ e.customerCode }}</a> } @else { - }</td>
                <td><b>{{ e.customerName }}</b></td>
                <td>{{ e.city || '-' }}</td>
                <td><span class="clip" [title]="e.title">{{ e.title }}</span></td>
                <td><span class="badge" [class.ok]="won(e)" [class.off]="e.stage === 'LOST'">{{ e.stageLabel }}</span></td>
                <td>{{ e.stageDate | date: 'dd MMM yyyy' }}</td>
                <td>{{ e.ownerName || '-' }}</td>
                <td class="r">@if (e.orderValue || e.expectedValue) { &#8377;{{ (e.orderValue || e.expectedValue) | number: '1.0-0' }} } @else { - }</td>
                <td>@if (e.nextFollowUp && open(e)) { <span class="badge" [class.off]="due(e)">{{ e.nextFollowUp | date: 'dd MMM yyyy' }}</span> } @else { - }</td>
                <td class="actions"><a [routerLink]="['/enquiries', e.id]">Open</a></td>
              </tr>
            }
          </tbody>
        </table></div>
      }
      @if (loaded && shown.length === 0) { <p class="muted">{{ rows.length ? 'No enquiries match.' : 'No enquiries yet. Click "New enquiry" when a customer contacts you.' }}</p> }
    </div>

    @if (f) {
      <div class="modal-bg"><div class="modal">
        <div class="modal-h"><h3>New enquiry</h3><button class="icon-btn" (click)="f = null" aria-label="Close"><app-icon name="x" /></button></div>
        <span class="lbl">Business line</span>
        <div class="seg" style="margin-bottom:12px">
          @for (l of lines; track l.key) { <button type="button" [class.on]="f.businessLine === l.key" (click)="f.businessLine = l.key">{{ l.label }}</button> }
        </div>
        <div class="grid">
          <label class="wide"><span>Customer <span class="req">*</span></span>
            <select [(ngModel)]="f.customerId"><option [ngValue]="null">Select a customer</option>@for (c of customers; track c.id) { <option [ngValue]="c.id">{{ c.code }} · {{ c.name }}{{ c.city ? ' - ' + c.city : '' }}</option> }</select>
            <span class="hint">Not in the list? <a routerLink="/customers" (click)="f = null">Add the customer</a> first.</span>
          </label>
          <label class="wide"><span>Requirement <span class="req">*</span></span><input [(ngModel)]="f.title" [placeholder]="f.businessLine === 'GENSET' ? 'e.g. 125 kVA DG set for hospital backup' : 'e.g. 50 kWp rooftop on-grid system'" /></label>
          <label>Capacity ({{ lineOf(f.businessLine).unit }})<input type="number" min="0" [(ngModel)]="f.capacity" /></label>
          <label>Source<select [(ngModel)]="f.source"><option value="">Select</option>@for (s of sources; track s) { <option [value]="s">{{ s }}</option> }</select></label>
          <label>Expected value (&#8377;)<input type="number" min="0" [(ngModel)]="f.expectedValue" /></label>
          <label>Next follow-up<input type="date" [(ngModel)]="f.nextFollowUp" /></label>
          @if (owners.length > 1) {
            <label>Owner<select [(ngModel)]="f.ownerId">@for (o of owners; track o.id) { <option [ngValue]="o.id">{{ o.name }}</option> }</select></label>
          }
          <label class="wide">Notes<textarea rows="2" [(ngModel)]="f.notes"></textarea></label>
        </div>
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px"><button [disabled]="busy" (click)="create()">Create enquiry</button><button class="ghost" (click)="f = null">Cancel</button></div>
      </div></div>
    }
  `,
})
export class EnquiriesComponent implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);
  lines = LINES; stages = STAGES; sources = SOURCES; lineOf = lineOf;
  rows: EnquiryRow[] = [];
  stats: EnquiryStats | null = null;
  customers: Customer[] = [];
  owners: { id: number; name: string }[] = [];
  view: 'board' | 'list' = (localStorage.getItem('enquiryView') as 'board' | 'list') || 'board';
  q = ''; lineF = ''; stageF = ''; ownerF: number | null = null; dueOnly = false;
  error = ''; loaded = false;
  f: Enquiry | null = null; formError = ''; busy = false;

  get openCount() { return this.rows.filter(e => this.open(e)).length; }
  open(e: EnquiryRow) { return STAGES.findIndex(s => s.key === e.stage) < 4; }
  won(e: EnquiryRow) { return e.stage !== 'LOST' && !this.open(e); }
  due(e: EnquiryRow) { return !!e.nextFollowUp && e.nextFollowUp <= today(); }
  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter(e =>
      (!this.lineF || e.businessLine === this.lineF) && (!this.stageF || e.stage === this.stageF) &&
      (this.ownerF == null || e.ownerId === this.ownerF) && (!this.dueOnly || (this.open(e) && this.due(e))) &&
      (!q || [e.refNo, e.customerCode, e.customerName, e.title, e.city, e.source].some(v => (v || '').toLowerCase().includes(q))));
  }
  inStage(key: string) { return this.shown.filter(e => e.stage === key); }

  ngOnInit() {
    this.load();
    this.api.enquiryOwners().subscribe(o => (this.owners = o));
    this.api.customers().subscribe({ next: c => (this.customers = c), error: () => (this.customers = []) });
    if (this.route.snapshot.queryParamMap.get('due')) this.dueOnly = true;
    const cust = Number(this.route.snapshot.queryParamMap.get('newFor'));
    if (cust) setTimeout(() => { this.openNew(); this.f!.customerId = cust; }, 0);
  }
  load() {
    this.api.enquiries().subscribe({ next: r => { this.rows = r; this.loaded = true; }, error: e => { this.loaded = true; this.error = e.error?.message || 'Could not load enquiries'; } });
    this.api.enquiryStats().subscribe(s => (this.stats = s));
  }
  setView(v: 'board' | 'list') { this.view = v; try { localStorage.setItem('enquiryView', v); } catch { } }

  openNew() {
    this.formError = '';
    this.f = { businessLine: 'GENSET', customerId: null, title: '', capacity: null, source: '', ownerId: null, expectedValue: null, nextFollowUp: null, notes: '' };
    this.auth.me().subscribe(m => { if (this.f && this.f.ownerId == null) this.f.ownerId = m.id; });
  }

  create() {
    if (!this.f) return;
    if (!this.f.customerId) { this.formError = 'Choose the customer.'; return; }
    if (!this.f.title.trim()) { this.formError = 'Describe the requirement.'; return; }
    this.busy = true; this.formError = '';
    this.api.saveEnquiry(this.f).subscribe({
      next: d => { this.busy = false; this.f = null; this.router.navigate(['/enquiries', d.enquiry.id]); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not create the enquiry'; },
    });
  }
}
