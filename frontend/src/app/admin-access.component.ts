import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api, ModuleInfo, RoleDto, UserDetail, UserDto } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';
import { RightsMatrixComponent } from './rights-matrix.component';

/** Administration > Grant access: pick a person, give them a role, then fine-tune the rights they need. */
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, RightsMatrixComponent],
  template: `
    <div class="page-h">
      <div><h1>Grant access</h1><p class="muted">Choose a user, give them a role, then tick exactly the rights they need. Saving applies both together.</p></div>
    </div>

    <div class="card">
      <div class="grid">
        <label><span>User <span class="req">*</span></span>
          <select [ngModel]="userId" (ngModelChange)="pickUser($event)">
            <option [ngValue]="null">Select a user</option>
            @for (u of users; track u.id) { <option [ngValue]="u.id">{{ u.name }} ({{ u.email }}){{ u.active ? '' : ' - inactive' }}</option> }
          </select>
        </label>
        <label><span>Role <span class="req">*</span></span>
          <select [ngModel]="roleId" (ngModelChange)="pickRole($event)" [disabled]="!d || isSelf">
            <option [ngValue]="null">Select a role</option>
            @for (r of roles; track r.id) { <option [ngValue]="r.id">{{ r.name }}{{ r.adminRole ? ' (full access)' : '' }}</option> }
          </select>
        </label>
      </div>
      @if (d) {
        <div class="kv" style="margin-top:14px">
          <div><small>Current role</small><b>{{ d.user.roleName || '-' }}</b></div>
          <div><small>Status</small><b>{{ d.user.active ? 'Active' : 'Inactive' }}</b></div>
          <div><small>Rights after saving</small><b>{{ role?.adminRole ? 'Everything' : rights.length + ' of ' + total }}</b></div>
          <div><small>Quotations visible</small><b>{{ role ? (role.adminRole || role.quotationScope === 'ALL' ? 'All records' : 'Own records only') : '-' }}</b></div>
        </div>
        @if (isSelf) { <p class="hint">This is your own account: administrators always have every right, and you cannot change your own role.</p> }
        @if (roleChanged) { <p class="notice">The role changes from <b>{{ d.user.roleName }}</b> to <b>{{ role?.name }}</b>. {{ d.user.name }} will be signed out and must sign in again.</p> }
      }
    </div>

    @if (d && role) {
      <div class="card">
        <div class="card-h">
          <div><h3>Rights</h3>
            <p>{{ role.adminRole ? 'The Admin role holds every right.' : 'Ticked = allowed. Green = given on top of the ' + role.name + ' role, red = taken away from it.' }}</p></div>
          @if (!role.adminRole && !isSelf) { <button class="ghost" [disabled]="atRoleDefaults" (click)="rights = roleRights(role)">Reset to role defaults</button> }
        </div>
        <rights-matrix [modules]="modules" [selected]="role.adminRole ? allRights : rights" [base]="role.adminRole ? null : role.rights"
                       [readonly]="role.adminRole || isSelf" (selectedChange)="rights = $event" />
        @if (!role.adminRole && (granted.length || removed.length)) {
          <p class="hint">{{ granted.length }} right(s) added beyond the role, {{ removed.length }} removed from it.</p>
        }
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      @if (!isSelf) {
        <button [disabled]="!dirty || busy" (click)="save()"><app-icon name="check" [size]="18" /> Save access</button>
        <a [routerLink]="['/admin/users', d.user.id]">Open user page</a>
      }
      @if (saved) { <span class="ok">Access saved</span> }
    }
  `,
})
export class AdminAccessComponent implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);
  users: UserDto[] = [];
  roles: RoleDto[] = [];
  modules: ModuleInfo[] = [];
  userId: number | null = null;
  roleId: number | null = null;
  d: UserDetail | null = null;
  rights: string[] = [];
  error = ''; busy = false; saved = false;
  private myId: number | null = null;

  get role() { return this.roles.find(r => r.id === this.roleId) ?? null; }
  get total() { return this.modules.filter(m => !m.adminOnly).reduce((n, m) => n + m.rights.length, 0); }
  get allRights() { return this.modules.flatMap(m => m.rights.map(r => `${m.key}:${r}`)); }
  get isSelf() { return !!this.d && this.d.user.id === this.myId; }
  get roleChanged() { return !!this.d && !!this.roleId && this.d.user.roleId !== this.roleId; }
  get granted() { const b = new Set(this.role?.rights ?? []); return this.rights.filter(k => !b.has(k)); }
  get removed() { const s = new Set(this.rights); return (this.role?.rights ?? []).filter(k => !s.has(k)); }
  get atRoleDefaults() { return !this.granted.length && !this.removed.length; }
  get dirty() {
    if (!this.d || !this.role) return false;
    if (this.roleChanged) return true;
    return [...this.rights].sort().join() !== [...this.d.permissions].sort().join();
  }

  ngOnInit() {
    this.api.modules().subscribe(m => (this.modules = m));
    this.api.roles().subscribe(r => (this.roles = r));
    this.auth.me().subscribe(m => (this.myId = m.id));
    this.api.adminUsers().subscribe(u => {
      this.users = [...u].sort((a, b) => a.name.localeCompare(b.name));
      const q = Number(this.route.snapshot.queryParamMap.get('user'));
      if (q && this.users.some(x => x.id === q)) this.pickUser(q);
    });
  }

  roleRights(r: RoleDto) { return r.adminRole ? this.allRights : [...r.rights]; }

  pickUser(id: number | null) {
    this.userId = id; this.d = null; this.roleId = null; this.rights = []; this.error = ''; this.saved = false;
    this.router.navigate([], { queryParams: { user: id ?? null }, replaceUrl: true });
    if (!id) return;
    this.api.adminUser(id).subscribe(d => { this.d = d; this.roleId = d.user.roleId; this.rights = [...d.permissions]; });
  }

  /** A new role starts from that role's own rights; the admin then adds or removes single rights. */
  pickRole(id: number | null) {
    this.roleId = id; this.saved = false;
    const r = this.role;
    if (!r || !this.d) return;
    this.rights = r.id === this.d.user.roleId ? [...this.d.permissions] : this.roleRights(r);
  }

  save() {
    if (!this.d || !this.roleId) return;
    const msg = this.roleChanged ? `Change ${this.d.user.name}'s role to ${this.role?.name}? They will be signed out.` : `Save ${this.d.user.name}'s rights?`;
    if (!confirm(msg)) return;
    this.busy = true; this.error = '';
    this.api.setUserAccess(this.d.user.id, this.roleId, this.role?.adminRole ? [] : this.rights).subscribe({
      next: d => {
        this.busy = false; this.d = d; this.roleId = d.user.roleId; this.rights = [...d.permissions];
        this.saved = true; setTimeout(() => (this.saved = false), 2500);
        this.api.adminUsers().subscribe(u => (this.users = [...u].sort((a, b) => a.name.localeCompare(b.name))));
        this.api.roles().subscribe(r => (this.roles = r));
      },
      error: e => { this.busy = false; this.error = e.error?.message || 'Could not save access'; },
    });
  }
}
