import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api, Overview } from './api';
import { IconComponent } from './icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, DatePipe, IconComponent],
  template: `
    <div class="page-h"><div><h1>Administration</h1><p class="muted">Users, rights and activity for your company.</p></div></div>

    @if (o) {
      <div class="stats">
        <div class="stat blue"><span class="ic"><app-icon name="users" /></span><div><small>Users</small><b>{{ o.users }}</b></div></div>
        <div class="stat green"><span class="ic"><app-icon name="check" /></span><div><small>Active users</small><b>{{ o.activeUsers }}</b></div></div>
        <div class="stat violet"><span class="ic"><app-icon name="shield" /></span><div><small>Roles</small><b>{{ o.roles }}</b></div></div>
        <div class="stat amber"><span class="ic"><app-icon name="key" /></span><div><small>Sign-ins today (UTC)</small><b>{{ o.signInsToday }}</b></div></div>
        <div class="stat blue"><span class="ic"><app-icon name="activity" /></span><div><small>Actions today (UTC)</small><b>{{ o.actionsToday }}</b></div></div>
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Recent activity</h3><p>The latest actions across the company.</p></div><a routerLink="/admin/activity">Open activity log</a></div>
        @if (o.recent.length === 0) { <p class="muted">No activity yet.</p> }
        @else {
          <div class="scroll"><table>
            <thead><tr><th>Time</th><th>User</th><th>Module</th><th>Action</th><th>Detail</th><th>IP</th></tr></thead>
            <tbody>
              @for (a of o.recent; track a.id) {
                <tr><td class="nowrap">{{ a.at | date: 'dd MMM, HH:mm' }}</td><td>{{ a.userName }}</td><td>{{ a.module }}</td><td><span class="badge">{{ a.action }}</span></td><td class="small">{{ a.detail }}</td><td class="small">{{ a.ip }}</td></tr>
              }
            </tbody>
          </table></div>
        }
      </div>
      <div class="card">
        <div class="card-h"><div><h3>Quick actions</h3></div></div>
        <a class="btn" routerLink="/admin/users"><app-icon name="users" [size]="18" /> Manage users</a>
        <a class="btn" routerLink="/admin/access"><app-icon name="unlock" [size]="18" /> Grant access</a>
        <a class="btn" routerLink="/admin/roles"><app-icon name="shield" [size]="18" /> Roles &amp; rights</a>
        <a class="btn" routerLink="/admin/activity"><app-icon name="activity" [size]="18" /> Activity log</a>
      </div>
    }
  `,
})
export class AdminOverviewComponent implements OnInit {
  private api = inject(Api);
  o?: Overview;
  ngOnInit() { this.api.adminOverview().subscribe(o => (this.o = o)); }
}
