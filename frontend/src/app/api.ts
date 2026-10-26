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
export interface Summary { id: number; refNo: string; clientName: string; projectLocation: string; quoteDate: string; total: number; }
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
