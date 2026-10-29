import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivityFilter, Api, AuditRow, ModuleInfo, SessionRow, UserDto } from './api';
import { IconComponent } from './icon.component';

const ACTIONS = ['CREATE', 'EDIT', 'DELETE', 'DOWNLOAD', 'SEND', 'ACTIVATE', 'DEACTIVATE', 'RESET_PASSWORD', 'PERMISSIONS', 'IMPORT', 'PASSWORD_CHANGE', 'LOGIN_FAILED'];

@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, IconComponent],
  template: `
    <div class="page-h"><div><h1>Activity log</h1><p class="muted">Sign-ins and every change, download and send.</p></div></div>
    <div class="tabs">
      <button [class.on]="tab === 'sessions'" (click)="setTab('sessions')">Sign-in history</button>
      <button [class.on]="tab === 'audit'" (click)="setTab('audit')">Audit trail</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <label>User<select [(ngModel)]="f.userId"><option [ngValue]="null">All users</option>@for (u of users; track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></label>
        @if (tab === 'audit') {
          <label>Module<select [(ngModel)]="f.module"><option value="">All modules</option><option value="AUTH">Sign-in (AUTH)</option>@for (m of modules; track m.key) { <option [value]="m.key">{{ m.label }}</option> }</select></label>
          <label>Action<select [(ngModel)]="f.action"><option value="">All actions</option>@for (a of actions; track a) { <option [value]="a">{{ a }}</option> }</select></label>
          <label>Search<input placeholder="Record or detail" [(ngModel)]="f.q" (keyup.enter)="apply()" /></label>
        }
        <label>From<input type="date" [(ngModel)]="f.from" /></label>
        <label>To<input type="date" [(ngModel)]="f.to" /></label>
        <button (click)="apply()"><app-icon name="search" [size]="18" /> Filter</button>
        <button class="ghost" (click)="clear()">Clear</button>
        @if (tab === 'audit') { <button class="ghost" (click)="api.auditCsv(f)"><app-icon name="download" [size]="18" /> Export CSV</button> }
      </div>
      @if (error) { <p class="error">{{ error }}</p> }

      @if (tab === 'sessions') {
        <div class="scroll"><table>
          <thead><tr><th>User</th><th>Portal</th><th>Signed in</th><th>Signed out</th><th>IP address</th><th>Device</th></tr></thead>
          <tbody>
            @for (s of sess; track s.id) {
              <tr>
                <td><b>{{ s.userName }}</b><br /><span class="muted small">{{ s.userEmail }}</span></td>
                <td>{{ s.portal }}</td>
                <td class="nowrap">{{ s.signedInAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                <td class="nowrap">
                  @if (s.signedOutAt) { {{ s.signedOutAt | date: 'dd MMM yyyy, HH:mm:ss' }} @if (s.signOutReason && s.signOutReason !== 'SIGN_OUT') { <br /><span class="badge">{{ s.signOutReason }}</span> } }
                  @else { <span class="muted">Not signed out</span> }
                </td>
                <td>{{ s.ip }}</td><td class="small">{{ s.userAgent }}</td>
              </tr>
            }
          </tbody>
        </table></div>
      } @else {
        <div class="scroll"><table>
          <thead><tr><th>Time</th><th>User</th><th>Module</th><th>Action</th><th>Record</th><th>Detail</th><th>IP</th></tr></thead>
          <tbody>
            @for (a of rows; track a.id) {
              <tr>
                <td class="nowrap">{{ a.at | date: 'dd MMM yyyy, HH:mm:ss' }}</td><td>{{ a.userName }}</td><td>{{ a.module }}</td>
                <td><span class="badge" [class.off]="a.action === 'LOGIN_FAILED' || a.action === 'DELETE'">{{ a.action }}</span></td>
                <td class="small">{{ a.entityType }} {{ a.entityId ? '#' + a.entityId : '' }}</td><td class="small">{{ a.detail }}</td><td class="small">{{ a.ip }}</td>
              </tr>
            }
          </tbody>
        </table></div>
      }
      @if (total === 0) { <p class="muted">Nothing found for these filters.</p> }

      <div class="pager">
        <span>{{ total }} record(s)</span>
        <button class="ghost" [disabled]="page === 0" (click)="go(page - 1)">Previous</button>
        <span>Page {{ page + 1 }} of {{ pages }}</span>
        <button class="ghost" [disabled]="page + 1 >= pages" (click)="go(page + 1)">Next</button>
      </div>
    </div>
  `,
})
export class AdminActivityComponent implements OnInit {
  api = inject(Api);
  tab: 'sessions' | 'audit' = 'sessions';
  users: UserDto[] = [];
  modules: ModuleInfo[] = [];
  actions = ACTIONS;
  f: ActivityFilter = { userId: null, module: '', action: '', q: '', from: '', to: '' };
  rows: AuditRow[] = [];
  sess: SessionRow[] = [];
  page = 0; total = 0; size = 25; error = '';

  get pages() { return Math.max(1, Math.ceil(this.total / this.size)); }

  ngOnInit() {
    this.api.adminUsers().subscribe(u => (this.users = u));
    this.api.modules().subscribe(m => (this.modules = m));
    this.go(0);
  }

  setTab(t: 'sessions' | 'audit') { this.tab = t; this.go(0); }
  apply() { this.go(0); }
  clear() { this.f = { userId: null, module: '', action: '', q: '', from: '', to: '' }; this.go(0); }

  go(p: number) {
    this.page = p; this.error = '';
    const fail = (e: any) => (this.error = e.error?.message || 'Could not load the log');
    if (this.tab === 'sessions') this.api.sessions(this.f, p, this.size).subscribe({ next: r => { this.sess = r.items; this.total = r.total; }, error: fail });
    else this.api.audit(this.f, p, this.size).subscribe({ next: r => { this.rows = r.items; this.total = r.total; }, error: fail });
  }
}
