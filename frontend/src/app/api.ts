import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Item {
  equipment: string; specification: string; make: string; uom: string;
  qty: number; unitPrice: number; imageKey: string | null;
}
export interface QSection {
  id?: number; position?: number; kind: string; title: string; content: string; style: string;
  visible: boolean; imageColumns: number; imageKeys: string[];
}
export interface Quote {
  id?: number; refNo: string; quoteDate: string; clientName: string; clientAddress: string;
  contactPerson: string; phone: string; projectLocation: string; capacityKw: number; gstPercent: number;
  showItemPrices: boolean; sections: QSection[];
  coverImageKey: string; signatoryName: string; signatoryTitle: string; signatoryPhone: string; signatoryEmail: string;
  items: Item[];
}
export interface Summary { id: number; refNo: string; clientName: string; projectLocation: string; quoteDate: string; total: number; createdBy: string; }
export interface Section { id: number; sectionKey: string; title: string; content: string; }
export interface Company {
  companyName: string; address: string; logoKey: string | null; coverImageKey: string | null; diagramImageKey: string | null;
  signatoryName: string; signatoryTitle: string; signatoryPhone: string; signatoryEmail: string;
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

  sections() { return this.http.get<Section[]>('/api/sections'); }
  saveSections(s: Section[]) { return this.http.put<Section[]>('/api/sections', s); }
  resetSections() { return this.http.post<Section[]>('/api/sections/reset', {}); }

  template() { return this.http.get<QSection[]>('/api/quotations/template'); }
  addSection(title: string, content: string) { return this.http.post<Section>('/api/sections', { title, content }); }
  deleteSection(id: number) { return this.http.delete(`/api/sections/${id}`); }

  account() { return this.http.get<{ email: string }>('/api/account'); }
  changeEmail(newEmail: string, currentPassword: string) { return this.http.put<{ email: string }>('/api/account/email', { newEmail, currentPassword }); }
  changePassword(currentPassword: string, newPassword: string) { return this.http.put('/api/account/password', { currentPassword, newPassword }); }

  company() { return this.http.get<Company>('/api/company'); }
  saveCompany(c: Company) { return this.http.put<Company>('/api/company', c); }
  catalog() { return this.http.get<CatalogEntry[]>('/api/catalog'); }

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
      URL.revokeObjectURL(a.href);
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
    this.http.get(`/api/quotations/${id}/pdf`, { params: { inline: 'true' }, responseType: 'blob' }).subscribe(b => window.open(URL.createObjectURL(b), '_blank'));
  }

  download(id: number, name: string) {
    this.http.get(`/api/quotations/${id}/pdf`, { responseType: 'blob' }).subscribe(b => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = `${(name || 'quotation').replace(/[^\w-]/g, '_')}.pdf`;
      a.click();
      URL.revokeObjectURL(a.href);
    });
  }
}
