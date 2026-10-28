import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api, AuditRow, ModuleInfo, SessionRow, UserDetail } from './api';
import { RightsMatrixComponent } from './rights-matrix.component';

@Component({
  standalone: true,
  imports: [RouterLink, DatePipe, RightsMatrixComponent],
  template: `
    <p><a routerLink="/admin/users">&larr; All users</a></p>
    @if (d) {
      <div class="page-h">
        <div><h1>{{ d.user.name }}</h1>
          <p class="muted">{{ d.user.email }}
            <span class="badge" [class.ok]="d.user.active" [class.off]="!d.user.active">{{ d.user.active ? 'Active' : 'Inactive' }}</span>
            @if (d.user.mustChangePassword) { <span class="badge warn">Password not set yet</span> }</p></div>
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Profile</h3></div></div>
        <div class="kv">
          <div><small>Mobile</small><b>{{ d.user.mobile }}</b></div>
          <div><small>Designation</small><b>{{ d.user.designation || '-' }}</b></div>
          <div><small>Department</small><b>{{ d.user.department || '-' }}</b></div>
          <div><small>Role</small><b>{{ d.user.roleName }}</b></div>
          <div><small>Created</small><b>{{ d.user.createdAt | date: 'dd MMM yyyy' }}</b></div>
          <div><small>Last sign-in</small><b>{{ d.user.lastLoginAt ? (d.user.lastLoginAt | date: 'dd MMM yyyy, HH:mm') : 'Never' }}</b></div>
        </div>
      </div>

      <div class="card">
        <div class="card-h">
          <div><h3>Permissions</h3>
            <p>Starts from the <b>{{ d.user.roleName }}</b> role. Tick to grant, untick to revoke; changes apply to this person only and take effect on their next request.</p></div>
        </div>
        @if (d.user.admin) {
          <p class="muted">Administrators always have every right. Change the person's role to restrict them.</p>
          <rights-matrix [modules]="modules" [selected]="d.permissions" [readonly]="true" />
        } @else {
          <rights-matrix [modules]="modules" [selected]="rights" [base]="d.rolePermissions" (selectedChange)="rights = $event" />
          <div style="margin-top:12px">
            <button [disabled]="!dirty" (click)="save()">Save permissions</button>
            <button class="ghost" [disabled]="!d.grants.length && !d.revokes.length" (click)="resetToRole()">Reset to role defaults</button>
            @if (saved) { <span class="ok">Saved</span> }
            @if (error) { <span class="error">{{ error }}</span> }
          </div>
          <p class="hint">Quotation visibility (own records only or all) is set on the role. Administration modules are for administrators only.</p>
        }
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Recent sign-ins</h3></div><a routerLink="/admin/activity">Full log</a></div>
        <div class="scroll"><table>
          <thead><tr><th>Signed in</th><th>Signed out</th><th>IP address</th><th>Portal</th></tr></thead>
          <tbody>
            @for (s of sessions; track s.id) {
              <tr><td class="nowrap">{{ s.signedInAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                <td class="nowrap">{{ s.signedOutAt ? (s.signedOutAt | date: 'dd MMM yyyy, HH:mm:ss') : '-' }} @if (s.signOutReason && s.signOutReason !== 'SIGN_OUT') { <span class="badge">{{ s.signOutReason }}</span> }</td>
                <td>{{ s.ip }}</td><td>{{ s.portal }}</td></tr>
            }
          </tbody>
        </table></div>
        @if (!sessions.length) { <p class="muted">No sign-ins yet.</p> }
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Recent actions</h3></div></div>
        <div class="scroll"><table>
          <thead><tr><th>Time</th><th>Module</th><th>Action</th><th>Detail</th><th>IP</th></tr></thead>
          <tbody>
            @for (a of actions; track a.id) {
              <tr><td class="nowrap">{{ a.at | date: 'dd MMM, HH:mm' }}</td><td>{{ a.module }}</td><td><span class="badge">{{ a.action }}</span></td><td class="small">{{ a.detail }}</td><td class="small">{{ a.ip }}</td></tr>
            }
          </tbody>
        </table></div>
        @if (!actions.length) { <p class="muted">No recorded actions yet.</p> }
      </div>
    }
  `,
})
export class AdminUserDetailComponent implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  d?: UserDetail;
  modules: ModuleInfo[] = [];
  rights: string[] = [];
  sessions: SessionRow[] = [];
  actions: AuditRow[] = [];
  saved = false; error = '';

  ngOnInit() {
    this.api.modules().subscribe(m => (this.modules = m));
    this.route.paramMap.subscribe(p => this.load(+p.get('id')!));
  }

  private load(id: number) {
    this.api.adminUser(id).subscribe(d => this.show(d));
    this.api.sessions({ userId: id }, 0, 8).subscribe(p => (this.sessions = p.items));
    this.api.audit({ userId: id }, 0, 10).subscribe(p => (this.actions = p.items));
  }
  private show(d: UserDetail) { this.d = d; this.rights = [...d.permissions]; }

  get dirty() {
    if (!this.d) return false;
    const a = [...this.rights].sort().join(), b = [...this.d.permissions].sort().join();
    return a !== b;
  }

  save() {
    if (!this.d) return;
    this.error = '';
    this.api.setUserPermissions(this.d.user.id, this.rights).subscribe({
      next: d => { this.show(d); this.saved = true; setTimeout(() => (this.saved = false), 2500); this.api.audit({ userId: d.user.id }, 0, 10).subscribe(p => (this.actions = p.items)); },
      error: e => (this.error = e.error?.message || 'Could not save'),
    });
  }

  resetToRole() {
    if (this.d && confirm('Remove all grants and revokes for this person and use the role defaults?'))
      this.api.resetUserPermissions(this.d.user.id).subscribe(d => this.show(d));
  }
}
