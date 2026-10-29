import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, ImportReport, RoleDto, UserDto, UserSave } from './api';
import { IconComponent } from './icon.component';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, IconComponent],
  template: `
    <div class="page-h">
      <div><h1>Users</h1><p class="muted">Create people one at a time or in bulk, give them a role, and switch them on or off.</p></div>
      <div>
        <button class="ghost" (click)="openImport()"><app-icon name="upload" [size]="18" /> Bulk import</button>
        <button (click)="openForm()"><app-icon name="plus" [size]="18" /> Add user</button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <label>Search<input placeholder="Name, email, mobile, department" [(ngModel)]="q" /></label>
        <label>Role<select [(ngModel)]="roleF"><option value="">All roles</option>@for (r of roles; track r.id) { <option [value]="r.id">{{ r.name }}</option> }</select></label>
        <label>Status<select [(ngModel)]="statusF"><option value="">All</option><option value="active">Active</option><option value="inactive">Inactive</option></select></label>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="scroll">
        <table>
          <thead><tr><th>User</th><th>Mobile</th><th>Designation / Department</th><th>Role</th><th>Status</th><th>Last sign-in</th><th>Rights</th><th></th></tr></thead>
          <tbody>
            @for (u of shown; track u.id) {
              <tr>
                <td><a [routerLink]="['/admin/users', u.id]"><b>{{ u.name }}</b></a><br /><span class="muted small">{{ u.email }}</span></td>
                <td class="nowrap">{{ u.mobile }}</td>
                <td>{{ u.designation }}<br /><span class="muted small">{{ u.department }}</span></td>
                <td>{{ u.roleName }}</td>
                <td>
                  <span class="badge" [class.ok]="u.active" [class.off]="!u.active">{{ u.active ? 'Active' : 'Inactive' }}</span>
                  @if (u.mustChangePassword) { <br /><span class="badge warn">Password not set yet</span> }
                </td>
                <td class="small nowrap">{{ u.lastLoginAt ? (u.lastLoginAt | date: 'dd MMM yyyy, HH:mm') : 'Never' }}</td>
                <td class="small">{{ u.admin ? 'All' : u.permissionCount }} @if (u.grantCount || u.revokeCount) { <span class="badge warn">customised</span> }</td>
                <td class="actions nowrap">
                  <a [routerLink]="['/admin/users', u.id]">Rights</a>
                  <button class="link" (click)="openForm(u)">Edit</button>
                  <button class="link" (click)="toggle(u)">{{ u.active ? 'Deactivate' : 'Activate' }}</button>
                  <button class="link" (click)="reset(u)">Reset password</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (shown.length === 0) { <p class="muted">No users match.</p> }
    </div>

    <!-- add / edit -->
    @if (form) {
      <div class="modal-bg" (click)="form = null"><div class="modal" (click)="$event.stopPropagation()">
        <div class="modal-h"><h3>{{ editing ? 'Edit user' : 'Add user' }}</h3><button class="icon-btn" (click)="form = null" aria-label="Close"><app-icon name="x" /></button></div>
        <div class="grid">
          <label>Name <span class="req">*</span><input [(ngModel)]="form.name" /></label>
          <label>Email <span class="req">*</span><input type="email" [(ngModel)]="form.email" /></label>
          <label>Mobile <span class="req">*</span><input [(ngModel)]="form.mobile" placeholder="+91 98765 43210" /></label>
          <label>Role <span class="req">*</span>
            <select [(ngModel)]="form.roleId"><option [ngValue]="null">Select a role</option>@for (r of roles; track r.id) { <option [ngValue]="r.id">{{ r.name }}</option> }</select>
          </label>
          <label>Designation<input [(ngModel)]="form.designation" /></label>
          <label>Department<input [(ngModel)]="form.department" /></label>
        </div>
        @if (!editing) { <p class="hint">A temporary password is generated. The person must set their own password when they first sign in.</p> }
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:12px"><button [disabled]="busy" (click)="submitForm()">{{ editing ? 'Save changes' : 'Create user' }}</button><button class="ghost" (click)="form = null">Cancel</button></div>
      </div></div>
    }

    <!-- temporary password shown once -->
    @if (secret) {
      <div class="modal-bg"><div class="modal">
        <h3>{{ secret.title }}</h3>
        <div class="notice">
          Share this temporary password with <b>{{ secret.who }}</b> securely. It is shown only once. They will be asked to choose their own password at first sign-in.
          <p><span class="secret">{{ secret.pw }}</span> <button class="link" (click)="copy(secret.pw)">Copy</button></p>
        </div>
        <button (click)="secret = null">Done</button>
      </div></div>
    }

    <!-- bulk import -->
    @if (imp) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><h3>Bulk user import</h3><button class="icon-btn" (click)="closeImport()" aria-label="Close"><app-icon name="x" /></button></div>

        @if (!report) {
          <p>1. Download the standard template. 2. Fill one row per person (max 200). 3. Upload it. You will see a row-by-row check before anything is created.</p>
          <p>
            <button class="ghost" (click)="template('xlsx')"><app-icon name="download" [size]="18" /> Excel template</button>
            <button class="ghost" (click)="template('csv')"><app-icon name="download" [size]="18" /> CSV template</button>
          </p>
          <label>Filled template (.xlsx or .csv)<input type="file" accept=".xlsx,.csv" (change)="pickFile($event)" /></label>
          @if (impError) { <p class="error">{{ impError }}</p> }
          <div style="margin-top:12px"><button [disabled]="!file || busy" (click)="check()">Check file</button></div>
        } @else {
          <div class="stats" style="margin-bottom:12px">
            <div class="stat blue"><div><small>Rows</small><b>{{ report.total }}</b></div></div>
            <div class="stat green"><div><small>{{ report.committed ? 'Created' : 'Ready to import' }}</small><b>{{ report.committed ? report.created : report.valid }}</b></div></div>
            <div class="stat amber"><div><small>With errors</small><b>{{ report.invalid }}</b></div></div>
          </div>

          @if (report.committed) {
            <div class="notice">
              {{ report.created }} user(s) created. Download the credentials now: temporary passwords are not shown again.
              <p><button (click)="downloadCredentials()"><app-icon name="download" [size]="18" /> Download credentials (CSV)</button></p>
            </div>
          } @else if (report.invalid > 0) {
            <p class="error">{{ report.invalid }} row(s) have problems and will be skipped. Fix them in your file and upload again, or import only the valid rows.</p>
          }

          <div class="scroll" style="max-height:340px;overflow:auto"><table>
            <thead><tr><th>Row</th><th>Name</th><th>Email</th><th>Mobile</th><th>Role</th><th>Result</th></tr></thead>
            <tbody>
              @for (r of report.rows; track r.row) {
                <tr [class.row-err]="r.errors.length" [class.row-ok]="!r.errors.length">
                  <td>{{ r.row }}</td><td>{{ r.name }}</td><td>{{ r.email }}</td><td>{{ r.mobile }}</td><td>{{ r.role }}</td>
                  <td class="small">@if (r.errors.length) { <span class="error">{{ r.errors.join('; ') }}</span> } @else { <span class="ok">{{ report.committed ? 'Created' : 'OK' }}</span> }</td>
                </tr>
              }
            </tbody>
          </table></div>

          <div style="margin-top:12px">
            @if (!report.committed) {
              <button [disabled]="report.valid === 0 || busy" (click)="commit()">Import {{ report.valid }} valid user(s)</button>
              @if (report.invalid > 0) { <button class="ghost" (click)="downloadErrors()"><app-icon name="download" [size]="18" /> Error report (CSV)</button> }
              <button class="ghost" (click)="report = null; file = null">Choose another file</button>
            } @else { <button (click)="closeImport()">Close</button> }
          </div>
          @if (impError) { <p class="error">{{ impError }}</p> }
        }
      </div></div>
    }
  `,
})
export class AdminUsersComponent implements OnInit {
  private api = inject(Api);
  users: UserDto[] = [];
  roles: RoleDto[] = [];
  q = ''; roleF = ''; statusF = '';
  error = '';

  form: (UserSave & { id?: number }) | null = null;
  editing = false; formError = ''; busy = false;
  secret: { title: string; who: string; pw: string } | null = null;

  imp = false; file: File | null = null; report: ImportReport | null = null; impError = '';

  ngOnInit() { this.load(); this.api.roles().subscribe(r => (this.roles = r)); }
  load() { this.api.adminUsers().subscribe({ next: u => (this.users = u), error: e => (this.error = e.error?.message || 'Could not load users') }); }

  get shown() {
    const q = this.q.trim().toLowerCase();
    return this.users.filter(u =>
      (!q || [u.name, u.email, u.mobile, u.department, u.designation].some(v => (v || '').toLowerCase().includes(q))) &&
      (!this.roleF || String(u.roleId) === this.roleF) &&
      (!this.statusF || (this.statusF === 'active') === u.active));
  }

  copy(t: string) { navigator.clipboard?.writeText(t); }

  openForm(u?: UserDto) {
    this.editing = !!u; this.formError = '';
    this.form = u ? { id: u.id, name: u.name, email: u.email, mobile: u.mobile, designation: u.designation || '', department: u.department || '', roleId: u.roleId }
                  : { name: '', email: '', mobile: '', designation: '', department: '', roleId: null };
  }

  submitForm() {
    if (!this.form) return;
    this.busy = true; this.formError = '';
    const f = this.form;
    if (f.id) {
      this.api.updateUser(f.id, f).subscribe({
        next: () => { this.busy = false; this.form = null; this.load(); },
        error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save'; },
      });
    } else {
      this.api.createUser(f).subscribe({
        next: r => { this.busy = false; this.form = null; this.secret = { title: 'User created', who: r.user.name, pw: r.tempPassword }; this.load(); },
        error: e => { this.busy = false; this.formError = e.error?.message || 'Could not create the user'; },
      });
    }
  }

  toggle(u: UserDto) {
    const verb = u.active ? 'Deactivate' : 'Activate';
    if (!confirm(`${verb} ${u.name}?${u.active ? ' They will be signed out immediately and cannot sign in until reactivated.' : ''}`)) return;
    this.api.setUserActive(u.id, !u.active).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not update') });
  }

  reset(u: UserDto) {
    if (!confirm(`Reset the password of ${u.name}? They will be signed out and must choose a new password at next sign-in.`)) return;
    this.api.resetUserPassword(u.id).subscribe({
      next: r => { this.secret = { title: 'Password reset', who: u.name, pw: r.tempPassword }; this.load(); },
      error: e => (this.error = e.error?.message || 'Could not reset'),
    });
  }

  // ---- bulk import ----
  openImport() { this.imp = true; this.file = null; this.report = null; this.impError = ''; }
  closeImport() { this.imp = false; this.load(); }
  pickFile(ev: Event) { this.file = (ev.target as HTMLInputElement).files?.[0] ?? null; this.impError = ''; }
  template(fmt: string) { this.api.saveBlob('/api/admin/users/import/template', `user-import-template.${fmt}`, { format: fmt }); }

  check() { this.run(false); }
  commit() { if (confirm(`Create ${this.report?.valid} user(s)?`)) this.run(true); }
  private run(commit: boolean) {
    if (!this.file) return;
    this.busy = true; this.impError = '';
    this.api.importUsers(this.file, commit).subscribe({
      next: r => { this.busy = false; this.report = r; },
      error: e => { this.busy = false; this.impError = e.error?.message || 'Could not read the file'; },
    });
  }

  private csv(rows: string[][], name: string) {
    const esc = (v: string) => { let s = (v ?? '').replace(/"/g, '""'); if (/^[=+\-@]/.test(s)) s = "'" + s; return `"${s}"`; };
    const blob = new Blob(['\uFEFF' + rows.map(r => r.map(esc).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  downloadErrors() {
    if (!this.report) return;
    this.csv([['Row', 'Name', 'Email', 'Mobile', 'Designation', 'Department', 'Role', 'Errors'],
      ...this.report.rows.filter(r => r.errors.length).map(r => [String(r.row), r.name, r.email, r.mobile, r.designation, r.department, r.role, r.errors.join('; ')])], 'user-import-errors.csv');
  }
  downloadCredentials() {
    if (!this.report) return;
    this.csv([['Name', 'Email', 'Role', 'Temporary password'],
      ...this.report.rows.filter(r => r.tempPassword).map(r => [r.name, r.email, r.role, r.tempPassword as string])], 'new-user-credentials.csv');
  }
}
