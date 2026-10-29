import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, ModuleInfo, RoleDto } from './api';
import { IconComponent } from './icon.component';
import { RightsMatrixComponent } from './rights-matrix.component';

@Component({
  standalone: true,
  imports: [FormsModule, IconComponent, RightsMatrixComponent],
  template: `
    <div class="page-h">
      <div><h1>Roles &amp; rights</h1><p class="muted">Ready-made sets of rights for each kind of user.</p></div>
      <button (click)="open()"><app-icon name="plus" [size]="18" /> New role</button>
    </div>

    @if (error) { <p class="error">{{ error }}</p> }
    <div class="card">
      <div class="scroll"><table>
        <thead><tr><th>Role</th><th>Description</th><th>Quotations visible</th><th>Rights</th><th>Users</th><th></th></tr></thead>
        <tbody>
          @for (r of roles; track r.id) {
            <tr>
              <td><b>{{ r.name }}</b> @if (r.systemRole) { <span class="badge">Built in</span> }</td>
              <td class="small">{{ r.description }}</td>
              <td>{{ r.quotationScope === 'ALL' ? 'All records' : 'Own records only' }}</td>
              <td>{{ r.adminRole ? 'Everything' : r.rights.length + ' of ' + total }}</td>
              <td>{{ r.userCount }}</td>
              <td class="actions nowrap">
                <button class="link" (click)="open(r)">{{ r.systemRole ? 'View' : 'Edit' }}</button>
                @if (!r.systemRole) { <button class="link danger" (click)="remove(r)">Delete</button> }
              </td>
            </tr>
          }
        </tbody>
      </table></div>
    </div>

    @if (f) {
      <div class="modal-bg"><div class="modal lg">
        <div class="modal-h"><h3>{{ f.id ? (f.systemRole ? f.name : 'Edit role') : 'New role' }}</h3><button class="icon-btn" (click)="f = null" aria-label="Close"><app-icon name="x" /></button></div>
        @if (f.systemRole) { <p class="muted">The Admin role is built in: it holds every right and signs in through Admin login.</p> }
        <div class="grid">
          <label><span>Role name <span class="req">*</span></span><input [(ngModel)]="f.name" [disabled]="!!f.systemRole" /></label>
          <label>Description<input [(ngModel)]="f.description" [disabled]="!!f.systemRole" /></label>
          <label>Quotation visibility
            <select [(ngModel)]="f.quotationScope" [disabled]="!!f.systemRole"><option value="OWN">Own records only</option><option value="ALL">All records</option></select>
          </label>
        </div>
        <h4 style="margin:18px 0 4px">Module rights</h4>
        <rights-matrix [modules]="modules" [selected]="f.rights" [readonly]="!!f.systemRole" (selectedChange)="f.rights = $event" />
        @if (formError) { <p class="error">{{ formError }}</p> }
        <div style="margin-top:14px">
          @if (!f.systemRole) { <button [disabled]="busy" (click)="save()">Save role</button> }
          <button class="ghost" (click)="f = null">{{ f.systemRole ? 'Close' : 'Cancel' }}</button>
        </div>
      </div></div>
    }
  `,
})
export class AdminRolesComponent implements OnInit {
  private api = inject(Api);
  roles: RoleDto[] = [];
  modules: ModuleInfo[] = [];
  f: (Partial<RoleDto> & { rights: string[] }) | null = null;
  error = ''; formError = ''; busy = false;

  get total() { return this.modules.filter(m => !m.adminOnly).reduce((n, m) => n + m.rights.length, 0); }

  ngOnInit() { this.api.modules().subscribe(m => (this.modules = m)); this.load(); }
  load() { this.api.roles().subscribe({ next: r => (this.roles = r), error: e => (this.error = e.error?.message || 'Could not load roles') }); }

  open(r?: RoleDto) {
    this.formError = '';
    this.f = r ? { ...r, rights: [...r.rights] } : { name: '', description: '', quotationScope: 'OWN', rights: ['QUOTATIONS:VIEW'] };
  }

  save() {
    if (!this.f) return;
    this.busy = true; this.formError = '';
    const body = { name: this.f.name, description: this.f.description, quotationScope: this.f.quotationScope, rights: this.f.rights };
    const req = this.f.id ? this.api.updateRole(this.f.id, body) : this.api.createRole(body);
    req.subscribe({
      next: () => { this.busy = false; this.f = null; this.load(); },
      error: e => { this.busy = false; this.formError = e.error?.message || 'Could not save the role'; },
    });
  }

  remove(r: RoleDto) {
    if (!confirm(`Delete the role "${r.name}"?`)) return;
    this.api.deleteRole(r.id).subscribe({ next: () => this.load(), error: e => (this.error = e.error?.message || 'Could not delete') });
  }
}
