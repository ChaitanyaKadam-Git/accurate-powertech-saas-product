import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, Summary } from './api';
import { IconComponent } from './icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe, FormsModule, IconComponent],
  template: `
    <div class="page-h">
      <div><h1>Statements</h1><p class="muted">Create, duplicate and download your proposals.</p></div>
      <a class="btn" routerLink="/quote/new"><app-icon name="plus" [size]="18" /> New statement</a>
    </div>
    <div class="card">
      <input class="search" placeholder="Search by client, reference or location" [(ngModel)]="query" />
      @if (shown.length === 0) { <p class="muted">No statements found.</p> }
      @else {
        <div class="scroll">
          <table>
            <thead><tr><th>Ref</th><th>Client</th><th>Location</th><th>Date</th><th class="r">Total (incl. GST)</th><th></th></tr></thead>
            <tbody>
              @for (r of shown; track r.id) {
                <tr>
                  <td>{{ r.refNo }}</td><td>{{ r.clientName }}</td><td>{{ r.projectLocation }}</td>
                  <td>{{ r.quoteDate | date: 'dd MMM yyyy' }}</td><td class="r">&#8377;{{ r.total | number: '1.2-2' }}</td>
                  <td class="actions">
                    <a [routerLink]="['/quote', r.id]">Edit</a>
                    <button class="link" (click)="api.download(r.id, r.refNo)">PDF</button>
                    <button class="link" (click)="copy(r.id)">Duplicate</button>
                    <button class="link danger" (click)="del(r.id)">Delete</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class QuoteListComponent implements OnInit {
  api = inject(Api);
  private router = inject(Router);
  rows: Summary[] = [];
  query = '';

  ngOnInit() { this.load(); }
  load() { this.api.list().subscribe(r => (this.rows = r)); }

  get shown() {
    const q = this.query.trim().toLowerCase();
    return q ? this.rows.filter(r => [r.refNo, r.clientName, r.projectLocation].some(v => (v || '').toLowerCase().includes(q))) : this.rows;
  }

  copy(id: number) { this.api.clone(id).subscribe(q => this.router.navigate(['/quote', q.id])); }
  del(id: number) { if (confirm('Delete this statement? This cannot be undone.')) this.api.remove(id).subscribe(() => this.load()); }
}
