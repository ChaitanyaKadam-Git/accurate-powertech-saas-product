import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Item {
  equipment: string; specification: string; make: string; uom: string;
  qty: number; unitPrice: number; imageKey: string | null;
}
/** Business lines: each has its own standard text, capacity unit, default GST and PDF wording (server: BusinessLine). */
export const LINES = [
  { key: 'SOLAR', label: 'Solar EPC', unit: 'kWp', gst: 8.9, capacity: 30 },
  { key: 'GENSET', label: 'Genset', unit: 'kVA', gst: 18, capacity: 62.5 },
] as const;
export type LineKey = typeof LINES[number]['key'];
export const lineOf = (k?: string | null) => LINES.find(l => l.key === k) ?? LINES[0];

/** Customer flow stages, in order (server: EnquiryStage). */
export const STAGES = [
  { key: 'NEW', label: 'New enquiry' },
  { key: 'SITE_VISIT', label: 'Site visit / survey' },
  { key: 'QUOTED', label: 'Quotation sent' },
  { key: 'NEGOTIATION', label: 'Negotiation' },
  { key: 'ORDER', label: 'Order received' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'INSTALLED', label: 'Installed' },
  { key: 'COMMISSIONED', label: 'Commissioned' },
  { key: 'LOST', label: 'Lost' },
] as const;
export const stageOf = (k?: string | null) => STAGES.find(s => s.key === k) ?? STAGES[0];
export const SOURCES = ['Website', 'Phone call', 'Email', 'Referral', 'Repeat customer', 'Walk-in', 'Tender', 'Exhibition', 'Other'];

export interface Enquiry {
  id?: number; refNo?: string; businessLine: string; customerId: number | null; title: string; capacity: number | null; source: string;
  ownerId: number | null; stage?: string; stageDate?: string; expectedValue: number | null; nextFollowUp: string | null; lostReason?: string | null;
  orderNo?: string | null; orderDate?: string | null; orderValue?: number | null; deliveryDate?: string | null; installationDate?: string | null;
  commissioningDate?: string | null; warrantyUntil?: string | null; amcUntil?: string | null; notes: string; createdAt?: string; updatedAt?: string;
}
export interface EnquiryRow {
  id: number; refNo: string; businessLine: string; customerId: number; customerCode: string | null; customerName: string; city: string | null; title: string;
  capacity: number | null; source: string | null; ownerId: number | null; ownerName: string | null; stage: string; stageLabel: string;
  stageDate: string | null; expectedValue: number | null; nextFollowUp: string | null; orderValue: number | null; quotationCount: number; updatedAt: string;
}
export interface EnquiryEvent { id: number; kind: string; stage: string | null; eventDate: string; text: string; userName: string; at: string; }
export interface EnquiryDetail {
  enquiry: Enquiry; customer: Customer | null; ownerName: string | null; stageLabel: string; events: EnquiryEvent[];
  quotations: { id: number; refNo: string; quoteDate: string; total: number }[];
}
export interface EnquiryStats { byStage: Record<string, number>; openValue: number; wonValueThisYear: number; wonThisYear: number; lostThisYear: number; followUps: EnquiryRow[]; }
export interface CustomerHistory { customer: Customer; enquiryIds: number[]; statements: { id: number; refNo: string; businessLine: string; quoteDate: string; total: number; enquiryId: number | null }[]; }

export interface QSection {
  id?: number; position?: number; kind: string; title: string; content: string; style: string;
  visible: boolean; imageColumns: number; imageKeys: string[];
}
export interface Quote {
  id?: number; businessLine: string; enquiryId?: number | null; customerId?: number | null; refNo: string; quoteDate: string; clientName: string; clientAddress: string;
  contactPerson: string; phone: string; projectLocation: string; capacityKw: number; gstPercent: number;
  showItemPrices: boolean; sections: QSection[];
  coverImageKey: string; signatoryName: string; signatoryTitle: string; signatoryPhone: string; signatoryEmail: string;
  items: Item[];
}
export interface Summary { id: number; businessLine: string; enquiryId?: number | null; customerId?: number | null; customerCode?: string | null; refNo: string; clientName: string; projectLocation: string; quoteDate: string; total: number; createdBy: string; }
export interface Section { id: number; sectionKey: string; businessLine?: string; title: string; content: string; }
export interface Company {
  companyName: string; address: string; logoKey: string | null; coverImageKey: string | null; diagramImageKey: string | null;
  signatoryName: string; signatoryTitle: string; signatoryPhone: string; signatoryEmail: string;
}
export interface Customer {
  id?: number; code?: string; name: string; contactPerson: string; phone: string; email: string; gstin: string;
  address: string; city: string; notes: string; createdAt?: string; updatedAt?: string;
}
/** Masters > Parts. businessLine is SOLAR, GENSET or BOTH. */
export interface Part {
  id?: number; name: string; category: string; businessLine: string; specification: string; make: string; uom: string;
  unitPrice: number; hsnCode: string; imageKey: string | null; active: boolean; updatedAt?: string; usedIn?: number;
}
export interface PartImportRow { row: number; name: string; make: string | null; category: string | null; uom: string | null; unitPrice: number; line: string; status: 'NEW' | 'UPDATE' | 'ERROR'; errors: string[]; }
export interface PartImportReport { committed: boolean; update: boolean; total: number; newCount: number; updateCount: number; invalid: number; created: number; updated: number; rows: PartImportRow[]; }
/** A product's part: the master part (read-only values) and the quantity. */
export interface ProductPart {
  id?: number; partId: number | null; qty: number;
  name?: string; category?: string; specification?: string; make?: string; uom?: string; unitPrice?: number; imageKey?: string | null; amount?: number; partActive?: boolean;
}
export interface Product {
  id?: number; businessLine: string; name: string; code: string; capacity: number | null; description: string; active: boolean;
  parts: ProductPart[]; total?: number; createdAt?: string; updatedAt?: string;
}
export interface CatalogEntry { equipment: string; specification: string; make: string; uom: string; unitPrice: number; imageKey: string | null; }

export interface DashStats {
  thisMonthCount: number; thisMonthValue: number; totalCount: number; totalValue: number; clients: number;
  months: { label: string; total: number; count: number }[];
  recent: { id: number; refNo: string; clientName: string; projectLocation: string; quoteDate: string; total: number }[];
}

// ---------- administration ----------
export interface ModuleInfo { key: string; label: string; group: string; adminOnly: boolean; rights: string[]; }
export interface RoleDto { id: number; name: string; description: string; systemRole: boolean; adminRole: boolean; quotationScope: string; rights: string[]; userCount: number; }
export interface UserDto {
  id: number; name: string; email: string; mobile: string; designation: string; department: string;
  roleId: number; roleName: string; admin: boolean; active: boolean; mustChangePassword: boolean;
  createdAt: string; lastLoginAt: string; grantCount: number; revokeCount: number; permissionCount: number;
}
export interface UserDetail { user: UserDto; rolePermissions: string[]; permissions: string[]; grants: string[]; revokes: string[]; }
export interface UserSave { name: string; email: string; mobile: string; designation: string; department: string; roleId: number | null; }
export interface ImportRow { row: number; name: string; email: string; mobile: string; designation: string; department: string; role: string; errors: string[]; tempPassword: string | null; }
export interface ImportReport { committed: boolean; total: number; valid: number; invalid: number; created: number; rows: ImportRow[]; }
export interface AuditRow { id: number; userId: number; userName: string; module: string; action: string; entityType: string; entityId: string; detail: string; ip: string; at: string; }
export interface SessionRow { id: number; userId: number; userName: string; userEmail: string; portal: string; signedInAt: string; signedOutAt: string | null; signOutReason: string | null; ip: string; userAgent: string; }
export interface PageOut<T> { items: T[]; total: number; page: number; size: number; }
export interface Overview { users: number; activeUsers: number; admins: number; roles: number; signInsToday: number; actionsToday: number; recent: AuditRow[]; }
export interface ActivityFilter { userId?: number | null; module?: string; action?: string; q?: string; from?: string; to?: string; }

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  dashboard() { return this.http.get<DashStats>('/api/dashboard'); }
  list() { return this.http.get<Summary[]>('/api/quotations'); }
  get(id: number) { return this.http.get<Quote>(`/api/quotations/${id}`); }
  save(q: Quote) { return q.id ? this.http.put<Quote>(`/api/quotations/${q.id}`, q) : this.http.post<Quote>('/api/quotations', q); }
  remove(id: number) { return this.http.delete(`/api/quotations/${id}`); }
  clone(id: number) { return this.http.post<Quote>(`/api/quotations/${id}/clone`, {}); }

  upload(file: File) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<{ key: string }>('/api/files', fd);
  }
  image(key: string) { return this.http.get('/api/files', { params: { key }, responseType: 'blob' }); }

  sections(line: string) { return this.http.get<Section[]>('/api/sections', { params: { line } }); }
  saveSections(line: string, s: Section[]) { return this.http.put<Section[]>('/api/sections', s, { params: { line } }); }
  resetSections(line: string) { return this.http.post<Section[]>('/api/sections/reset', {}, { params: { line } }); }

  template(line: string) { return this.http.get<QSection[]>('/api/quotations/template', { params: { line } }); }
  addSection(line: string, title: string, content: string) { return this.http.post<Section>('/api/sections', { title, content }, { params: { line } }); }
  deleteSection(id: number) { return this.http.delete(`/api/sections/${id}`); }

  account() { return this.http.get<{ email: string }>('/api/account'); }
  changeEmail(newEmail: string, currentPassword: string) { return this.http.put<{ email: string }>('/api/account/email', { newEmail, currentPassword }); }
  changePassword(currentPassword: string, newPassword: string) { return this.http.put('/api/account/password', { currentPassword, newPassword }); }

  company() { return this.http.get<Company>('/api/company'); }
  saveCompany(c: Company) { return this.http.put<Company>('/api/company', c); }
  catalog() { return this.http.get<CatalogEntry[]>('/api/catalog'); }

  enquiries(params: Record<string, string> = {}) { return this.http.get<EnquiryRow[]>('/api/enquiries', { params }); }
  enquiry(id: number) { return this.http.get<EnquiryDetail>(`/api/enquiries/${id}`); }
  enquiryStats() { return this.http.get<EnquiryStats>('/api/enquiries/stats'); }
  enquiryOwners() { return this.http.get<{ id: number; name: string }[]>('/api/enquiries/owners'); }
  saveEnquiry(e: Enquiry) { return e.id ? this.http.put<EnquiryDetail>(`/api/enquiries/${e.id}`, e) : this.http.post<EnquiryDetail>('/api/enquiries', e); }
  moveEnquiry(id: number, body: { stage: string; date?: string | null; note?: string; lostReason?: string; orderNo?: string; orderValue?: number | null; warrantyUntil?: string | null; amcUntil?: string | null }) {
    return this.http.post<EnquiryDetail>(`/api/enquiries/${id}/stage`, body);
  }
  addEnquiryNote(id: number, body: { kind: string; date?: string | null; text: string; nextFollowUp?: string | null }) { return this.http.post<EnquiryDetail>(`/api/enquiries/${id}/events`, body); }
  deleteEnquiry(id: number) { return this.http.delete(`/api/enquiries/${id}`); }
  customerHistory(id: number) { return this.http.get<CustomerHistory>(`/api/customers/${id}/history`); }
  customers() { return this.http.get<Customer[]>('/api/customers'); }
  saveCustomer(c: Customer) { return c.id ? this.http.put<Customer>(`/api/customers/${c.id}`, c) : this.http.post<Customer>('/api/customers', c); }
  parts(line?: string) { return this.http.get<Part[]>('/api/parts', { params: line ? { line } : {} }); }
  savePart(p: Part) { return p.id ? this.http.put<Part>(`/api/parts/${p.id}`, p) : this.http.post<Part>('/api/parts', p); }
  importParts(file: File, commit: boolean, update: boolean) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<PartImportReport>('/api/parts/import', fd, { params: { commit, update } });
  }
  deletePart(id: number) { return this.http.delete(`/api/parts/${id}`); }
  products(line?: string) { return this.http.get<Product[]>('/api/products', { params: line ? { line } : {} }); }
  saveProduct(p: Product) { return p.id ? this.http.put<Product>(`/api/products/${p.id}`, p) : this.http.post<Product>('/api/products', p); }
  cloneProduct(id: number) { return this.http.post<Product>(`/api/products/${id}/clone`, {}); }
  deleteProduct(id: number) { return this.http.delete(`/api/products/${id}`); }
  deleteCustomer(id: number) { return this.http.delete(`/api/customers/${id}`); }

  // ---- administration ----
  adminOverview() { return this.http.get<Overview>('/api/admin/overview'); }
  modules() { return this.http.get<ModuleInfo[]>('/api/admin/modules'); }
  roles() { return this.http.get<RoleDto[]>('/api/admin/roles'); }
  createRole(r: Partial<RoleDto>) { return this.http.post<RoleDto>('/api/admin/roles', r); }
  updateRole(id: number, r: Partial<RoleDto>) { return this.http.put<RoleDto>(`/api/admin/roles/${id}`, r); }
  deleteRole(id: number) { return this.http.delete(`/api/admin/roles/${id}`); }

  adminUsers() { return this.http.get<UserDto[]>('/api/admin/users'); }
  adminUser(id: number) { return this.http.get<UserDetail>(`/api/admin/users/${id}`); }
  createUser(u: UserSave) { return this.http.post<{ user: UserDto; tempPassword: string }>('/api/admin/users', u); }
  updateUser(id: number, u: UserSave) { return this.http.put<UserDto>(`/api/admin/users/${id}`, u); }
  setUserActive(id: number, active: boolean) { return this.http.post<UserDto>(`/api/admin/users/${id}/active`, { active }); }
  resetUserPassword(id: number) { return this.http.post<{ tempPassword: string }>(`/api/admin/users/${id}/reset-password`, {}); }
  setUserPermissions(id: number, rights: string[]) { return this.http.put<UserDetail>(`/api/admin/users/${id}/permissions`, { rights }); }
  setUserAccess(id: number, roleId: number, rights: string[]) { return this.http.put<UserDetail>(`/api/admin/users/${id}/access`, { roleId, rights }); }
  resetUserPermissions(id: number) { return this.http.post<UserDetail>(`/api/admin/users/${id}/permissions/reset`, {}); }
  importUsers(file: File, commit: boolean) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ImportReport>(`/api/admin/users/import?commit=${commit}`, fd);
  }
  saveBlob(url: string, filename: string, params?: Record<string, string>) {
    this.http.get(url, { responseType: 'blob', params }).subscribe(b => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    });
  }

  private qs(f: ActivityFilter, page: number, size: number) {
    const p: Record<string, string> = { page: String(page), size: String(size) };
    if (f.userId) p['userId'] = String(f.userId);
    if (f.module) p['module'] = f.module;
    if (f.action) p['action'] = f.action;
    if (f.q) p['q'] = f.q;
    if (f.from) p['from'] = new Date(f.from + 'T00:00:00').toISOString();                 // start of the chosen day, local time
    if (f.to) { const d = new Date(f.to + 'T00:00:00'); d.setDate(d.getDate() + 1); p['to'] = d.toISOString(); }   // through the end of that day
    return p;
  }
  audit(f: ActivityFilter, page: number, size = 25) { return this.http.get<PageOut<AuditRow>>('/api/admin/activity/audit', { params: this.qs(f, page, size) }); }
  sessions(f: ActivityFilter, page: number, size = 25) { return this.http.get<PageOut<SessionRow>>('/api/admin/activity/sessions', { params: this.qs(f, page, size) }); }
  auditCsv(f: ActivityFilter) { const p = this.qs(f, 0, 25); delete p['page']; delete p['size']; this.saveBlob('/api/admin/activity/audit.csv', 'audit-trail.csv', p); }

  /** Opens the PDF in a new tab (needs PDF "view"). */
  preview(id: number) {
    const tab = window.open('', '_blank');   // opened during the click, so popup blockers allow it
    this.http.get(`/api/quotations/${id}/pdf`, { params: { inline: 'true' }, responseType: 'blob' }).subscribe({
      next: b => { const url = URL.createObjectURL(b); if (tab) tab.location.href = url; else window.open(url, '_blank'); },
      error: () => tab?.close(),
    });
  }

  download(id: number, name: string) {
    this.http.get(`/api/quotations/${id}/pdf`, { responseType: 'blob' }).subscribe(b => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = `${(name || 'quotation').replace(/[^\w-]/g, '_')}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    });
  }
}
