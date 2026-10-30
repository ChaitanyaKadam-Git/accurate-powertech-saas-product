import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api, CustomerHistory, EnquiryRow, lineOf } from './api';
import { AuthService } from './auth';
import { IconComponent } from './icon.component';

/** One customer: contact details, every enquiry (customer flow) and every statement, with totals. */
@Component({
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, IconComponent],
  template: `
    <p><a routerLink="/customers">&larr; All customers</a></p>
    @if (h; as h) {
      <div class="page-h">
        <div><h1>{{ h.customer.name }} <span class="code">{{ h.customer.code }}</span></h1>
          <p class="muted">{{ h.customer.contactPerson || '' }}{{ h.customer.phone ? ' · ' + h.customer.phone : '' }}{{ h.customer.email ? ' · ' + h.customer.email : '' }}{{ h.customer.city ? ' · ' + h.customer.city : '' }}</p></div>
        <div>
          @if (auth.can('ENQUIRIES', 'CREATE')) { <a class="btn" routerLink="/enquiries" [queryParams]="{ newFor: h.customer.id }"><app-icon name="plus" [size]="18" /> New enquiry</a> }
        </div>
      </div>

      <div class="stats">
        <div class="stat blue"><span class="ic"><app-icon name="funnel" /></span><div><small>Enquiries</small><b>{{ enquiries.length }}</b></div></div>
        <div class="stat green"><span class="ic"><app-icon name="check" /></span><div><small>Orders</small><b>{{ orders }}</b></div></div>
        <div class="stat violet"><span class="ic"><app-icon name="file" /></span><div><small>Statements</small><b>{{ h.statements.length }}</b></div></div>
        <div class="stat amber"><span class="ic"><app-icon name="cash" /></span><div><small>Order value</small><b>&#8377;{{ orderValue | number: '1.0-0' }}</b></div></div>
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Enquiries</h3><p>Where each opportunity is in the customer flow.</p></div></div>
        <div class="scroll"><table>
          <thead><tr><th>Enquiry</th><th>Stage</th><th>Owner</th><th class="r">Value</th><th></th></tr></thead>
          <tbody>
            @for (e of enquiries; track e.id) {
              <tr>
                <td class="w-main"><b>{{ e.refNo }}</b> <span class="badge">{{ lineOf(e.businessLine).label }}</span><br /><span class="small">{{ e.title }}</span></td>
                <td><span class="badge" [class.ok]="won(e)" [class.off]="e.stage === 'LOST'">{{ e.stageLabel }}</span><br /><span class="muted small">{{ e.stageDate | date: 'dd MMM yyyy' }}</span></td>
                <td>{{ e.ownerName || '-' }}</td>
                <td class="r nowrap">@if (e.orderValue || e.expectedValue) { &#8377;{{ (e.orderValue || e.expectedValue) | number: '1.0-0' }} } @else { - }</td>
                <td class="actions stack-actions"><a [routerLink]="['/enquiries', e.id]">Open</a></td>
              </tr>
            }
          </tbody>
        </table></div>
        @if (!enquiries.length) { <p class="muted">No enquiries for this customer yet.</p> }
      </div>

      <div class="card">
        <div class="card-h"><div><h3>Statements</h3><p>Quotations for this customer (linked to an enquiry, or older ones with the same client name).</p></div></div>
        <div class="scroll"><table>
          <thead><tr><th>Ref</th><th>Line</th><th>Date</th><th>Enquiry</th><th class="r">Total</th><th></th></tr></thead>
          <tbody>
            @for (s of h.statements; track s.id) {
              <tr>
                <td><b>{{ s.refNo }}</b></td>
                <td><span class="badge">{{ lineOf(s.businessLine).label }}</span></td>
                <td>{{ s.quoteDate | date: 'dd MMM yyyy' }}</td>
                <td>@if (s.enquiryId) { <a [routerLink]="['/enquiries', s.enquiryId]">{{ refOf(s.enquiryId) }}</a> } @else { - }</td>
                <td class="r nowrap">&#8377;{{ s.total | number: '1.2-2' }}</td>
                <td class="actions stack-actions"><a [routerLink]="['/quote', s.id]">Open</a></td>
              </tr>
            }
          </tbody>
        </table></div>
        @if (!h.statements.length) { <p class="muted">No statements yet.</p> }
      </div>
    }
    @if (error) { <p class="error">{{ error }}</p> }
  `,
})
export class CustomerDetailComponent implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);
  lineOf = lineOf;
  h: CustomerHistory | null = null;
  enquiries: EnquiryRow[] = [];
  error = '';

  won(e: EnquiryRow) { return ['ORDER', 'DELIVERED', 'INSTALLED', 'COMMISSIONED'].includes(e.stage); }
  get orders() { return this.enquiries.filter(e => this.won(e)).length; }
  get orderValue() { return this.enquiries.filter(e => this.won(e)).reduce((s, e) => s + (e.orderValue || e.expectedValue || 0), 0); }
  refOf(id: number) { return this.enquiries.find(e => e.id === id)?.refNo ?? 'Enquiry'; }

  ngOnInit() {
    this.route.paramMap.subscribe(p => {
      const id = +p.get('id')!;
      this.api.customerHistory(id).subscribe({ next: h => (this.h = h), error: e => (this.error = e.error?.message || 'Could not load the customer') });
      this.api.enquiries({ customerId: String(id) }).subscribe({ next: r => (this.enquiries = r), error: () => (this.enquiries = []) });
    });
  }
}
