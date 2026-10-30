import { Component, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api, CatalogEntry, Customer, DocTemplate, INVOICES_ENABLED, Item, LINES, Part, Product, QSection, Summary, docTypeOf, lineOf } from './api';
import { AuthService } from './auth';
import { ImagePickerComponent } from './image-picker.component';

/** Today as yyyy-mm-dd in the browser's time zone. */
const localDate = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, DecimalPipe, ImagePickerComponent, RouterLink],
  template: `
    <div class="page-h">
      <div><h1>{{ id ? (form.value.refNo || docLabel) : 'New statement' }} <span class="badge">{{ docLabel }}</span> <span class="badge">{{ line.label }}</span>
        @if (enquiryRef) { <a class="badge" [routerLink]="['/enquiries', form.value.enquiryId]">For {{ enquiryRef }}</a> }
        @if (customerCode) { <a class="code" [routerLink]="['/customers', form.value.customerId]">{{ customerCode }}</a> }</h1>
        <p class="muted">{{ id ? (form.value.clientName || '') : 'Client details, PDF sections and items for a new proposal.' }}</p></div>
      @if (invoicesOn && id && auth.can('QUOTATIONS', 'CREATE') && form.value.docType !== 'INVOICE') {
        <div>
          @if (form.value.docType === 'QUOTATION') { <button type="button" class="ghost" (click)="convert('PROFORMA')">Create proforma</button> }
          <button type="button" (click)="convert('INVOICE')">Create tax invoice</button>
        </div>
      }
    </div>

    @if (!id) {
      <div class="card">
        <span class="lbl">Business line</span>
        <div class="seg">
          @for (l of lines; track l.key) {
            <button type="button" [class.on]="form.value.businessLine === l.key" (click)="setLine(l.key)">{{ l.label }}</button>
          }
        </div>
        <p class="hint">Sets the standard text, PDF wording, capacity unit ({{ line.unit }}) and default GST ({{ line.gst }}%).</p>
      </div>
    }

    @if (!id) {
      <div class="card">
        <label>Start from a previous statement (copies all sections with their photos, GST and all items)
          <select (change)="copyFrom($any($event.target).value)">
            <option value="">Start from blank</option>
            @for (s of previous; track s.id) { <option [value]="s.id">{{ s.refNo }} - {{ s.clientName }}</option> }
          </select>
        </label>
        <p class="hint">Client name, address, contact and location are not copied. Enter them below.</p>
      </div>
    }

    <details class="card">
      <summary>Add many items at once from Excel</summary>
      <p>Copy rows with these columns: equipment, specification, make, UOM, qty, unit price. Then paste here.</p>
      <textarea rows="5" [(ngModel)]="paste"></textarea>
      <button type="button" (click)="importPaste()">Add pasted rows</button>
    </details>

    <datalist id="catalog">
      @for (e of catalog; track e.equipment) { <option [value]="e.equipment"></option> }
    </datalist>

    <form [formGroup]="form" (ngSubmit)="save(false)">
      <div class="card grid">
        @if (customers.length && canSave) {
          <label class="wide">Customer (fills in the client details below)
            <select (change)="pickCustomer($any($event.target).value)">
              <option value="">Select a saved customer</option>
              @for (c of customers; track c.id) { <option [value]="c.id">{{ c.code }} · {{ c.name }}{{ c.city ? ' - ' + c.city : '' }}</option> }
            </select>
          </label>
        }
        <label>Reference no.<input formControlName="refNo" placeholder="Filled in automatically" /></label>
        <label>Date<input type="date" formControlName="quoteDate" /></label>
        <label>Client name<input formControlName="clientName" /></label>
        <label>Contact person<input formControlName="contactPerson" /></label>
        <label>Phone<input formControlName="phone" inputmode="numeric" maxlength="10" placeholder="10-digit number" (input)="digitsOnly($event)" />
          @if (form.controls.phone.invalid) { <span class="error small">Phone must be digits only, up to 10 digits.</span> }</label>
        <label>{{ isInvoice ? 'Place of supply / site' : 'Project location' }}<input formControlName="projectLocation" /></label>
        @if (isInvoice) {
          <label>Buyer's order / PO no.<input formControlName="poNo" /></label>
          <label>Due date<input type="date" formControlName="dueDate" /></label>
        }
        <label class="wide">Client address<input formControlName="clientAddress" /></label>
        <label>Capacity ({{ line.unit }})<input type="number" min="0" formControlName="capacityKw" (keydown)="blockSign($event)" />
          @if (form.controls.capacityKw.invalid) { <span class="error small">Capacity cannot be negative.</span> }</label>
        <label>GST %<input type="number" min="0" step="0.1" formControlName="gstPercent" (keydown)="blockSign($event)" />
          @if (form.controls.gstPercent.invalid) { <span class="error small">GST cannot be negative.</span> }</label>
        <label class="check"><input type="checkbox" formControlName="showItemPrices" /> Show item prices in the list</label>
      </div>

      <div class="card">
        <div class="row"><h3>PDF sections ({{ sections.length }})</h3><button type="button" (click)="addSection()">Add section</button></div>
        <p class="hint">The order here is the order in the PDF. Every visible section is listed in the table of contents. Sections with no text and no photos are skipped.</p>
        <div formArrayName="sections">
          @for (g of sections.controls; track g; let i = $index) {
            <div class="sec" [formGroupName]="i">
              <div class="row">
                <input class="grow" formControlName="title" placeholder="Section title" />
                <label class="check"><input type="checkbox" formControlName="visible" /> Show</label>
                <button type="button" class="link" [disabled]="i === 0" (click)="move(i, -1)">Up</button>
                <button type="button" class="link" [disabled]="i === sections.length - 1" (click)="move(i, 1)">Down</button>
                <button type="button" class="link danger" (click)="sections.removeAt(i)">Remove</button>
              </div>
              @if (g.value.kind === 'TEXT') {
                <label>Format
                  <select formControlName="style">
                    <option value="BULLETS">Bullet points (one per line)</option>
                    <option value="PARAGRAPH">Paragraphs (one per line)</option>
                    <option value="TABLE">Table (write each row as Topic | Details)</option>
                  </select>
                </label>
                <textarea rows="5" formControlName="content"></textarea>
              } @else {
                <p class="hint">{{ g.value.kind === 'BOM' ? 'The item table from the Items section below is printed here.' : 'The price summary with GST is printed here.' }}</p>
              }
              <div class="photos">
                @for (k of g.value.imageKeys; track $index) {
                  <span class="thumb">
                    @if (previews[k]) { <img [src]="previews[k]" alt="Section photo" /> }
                    <button type="button" class="link danger" (click)="removeImage(i, $index)">Remove</button>
                  </span>
                }
                <label class="filebtn">Add photos (select one or many)
                  <input type="file" accept="image/*" multiple (change)="onSectionFiles($event, i)" />
                </label>
                <label>Photos per row
                  <select formControlName="imageColumns">
                    <option [ngValue]="1">1</option><option [ngValue]="2">2</option><option [ngValue]="3">3</option>
                  </select>
                </label>
              </div>
            </div>
          }
        </div>
      </div>

      <div class="card">
        <div class="grid" style="margin-bottom:12px">
          @if (!id && lineTemplates.length) {
            <label>Template
              <select [ngModel]="form.value.templateId" [ngModelOptions]="{ standalone: true }" (ngModelChange)="pickTemplate($event)">
                @for (t of lineTemplates; track t.id) { <option [ngValue]="t.id">{{ t.name }}{{ t.isDefault ? ' (default)' : '' }}</option> }
              </select>
            </label>
          }
          <label>Title on the PDF<input formControlName="pdfTitle" placeholder="Empty = built-in title" /></label>
          @if (!isInvoice) {
            <label class="check"><input type="checkbox" formControlName="showCover" /> Cover page</label>
            <label class="check"><input type="checkbox" formControlName="showLetter" /> Cover letter</label>
          }
        </div>
        <image-picker label="Cover page image for this statement (leave empty to use the company default)"
          [key]="form.value.coverImageKey" (keyChange)="form.patchValue({ coverImageKey: $event ?? '' })" />
        <details>
          <summary>Signatory for this statement (leave empty to use the company default)</summary>
          <div class="grid">
            <label>Name<input formControlName="signatoryName" /></label>
            <label>Designation<input formControlName="signatoryTitle" /></label>
            <label>Phone<input formControlName="signatoryPhone" /></label>
            <label>Email<input formControlName="signatoryEmail" /></label>
          </div>
        </details>
      </div>

      <div class="card">
        <div class="row"><h3>Items ({{ items.length }})</h3>
          <div>
            @if (items.length) { <button type="button" class="ghost danger" (click)="removeAll()">Remove all</button> }
            <button type="button" (click)="addItem()">Add item</button>
          </div></div>
        @if (lineProducts.length && canSave) {
          <label>Add a product (copies all its parts into the items)
            <select #prodSel (change)="addProduct(prodSel.value); prodSel.value = ''">
              <option value="">Select a {{ line.label }} product</option>
              @for (p of lineProducts; track p.id) { <option [value]="p.id">{{ p.name }}{{ p.capacity != null ? ' - ' + p.capacity + ' ' + line.unit : '' }} ({{ p.parts.length }} parts)</option> }
            </select>
          </label>
        }
        @if (lineParts.length && canSave) {
          <label>Add a part
            <select #partSel (change)="addPart(partSel.value); partSel.value = ''">
              <option value="">Select a {{ line.label }} part</option>
              @for (p of lineParts; track p.id) { <option [value]="p.id">{{ p.category ? p.category + ': ' : '' }}{{ p.name }}{{ p.make ? ' (' + p.make + ')' : '' }}</option> }
            </select>
          </label>
        }
        <p class="hint">Or type an equipment name: parts from the master and items used before are suggested and fill in the rest.</p>
        <div class="scroll">
          <table class="items">
            <thead><tr><th>#</th><th>Equipment</th><th>Specification</th><th>Make</th><th>HSN</th><th>UOM</th><th>Qty</th><th>Unit price</th><th class="r">Amount</th><th>Photo</th><th></th></tr></thead>
            <tbody formArrayName="items">
              @for (g of items.controls; track g; let i = $index) {
                <tr [formGroupName]="i">
                  <td>{{ i + 1 }}</td>
                  <td><input formControlName="equipment" list="catalog" (change)="fillFromCatalog(i)" /></td>
                  <td><textarea rows="2" formControlName="specification"></textarea></td>
                  <td><input formControlName="make" /></td>
                  <td><input class="s" formControlName="hsn" maxlength="8" /></td>
                  <td><input class="s" formControlName="uom" /></td>
                  <td><input class="s" type="number" formControlName="qty" /></td>
                  <td><input class="m" type="number" formControlName="unitPrice" /></td>
                  <td class="r">{{ g.value.qty * g.value.unitPrice | number: '1.2-2' }}</td>
                  <td>
                    @if (g.value.imageKey && previews[g.value.imageKey]) { <img [src]="previews[g.value.imageKey]" width="48" alt="Item photo" /> }
                    <input type="file" accept="image/*" (change)="onFile($event, i)" />
                  </td>
                  <td><button type="button" class="link danger" (click)="items.removeAt(i)">Remove</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="totals">
          <div>Subtotal <b>{{ subtotal | number: '1.2-2' }}</b></div>
          <div>GST ({{ form.value.gstPercent }}%) <b>{{ gst | number: '1.2-2' }}</b></div>
          <div class="grand">Total <b>{{ total | number: '1.2-2' }}</b></div>
        </div>
      </div>

      @if (error) { <p class="error">{{ error }}</p> }
      @if (canSave) {
        <button type="submit">Save</button>
        @if (auth.can('PDF', 'DOWNLOAD')) { <button type="button" (click)="save(true)">Save and download PDF</button> }
      } @else { <p class="hint">You have view-only access to this statement.</p> }
    </form>
  `,
})
export class QuoteFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);

  id?: number;
  paste = '';
  error = '';
  previews: Record<string, string> = {};
  catalog: CatalogEntry[] = [];
  previous: Summary[] = [];
  customers: Customer[] = [];
  templates: DocTemplate[] = [];
  get lineTemplates() { return this.templates.filter(t => t.active && t.docType === (this.form.value.docType || 'QUOTATION') && t.businessLine === (this.form.value.businessLine || 'SOLAR')); }
  products: Product[] = [];
  masterParts: Part[] = [];
  get lineParts() { const l = this.form.value.businessLine || 'SOLAR'; return this.masterParts.filter(p => p.active && (p.businessLine === 'BOTH' || p.businessLine === l)); }
  get lineProducts() { return this.products.filter(p => p.active && p.businessLine === (this.form.value.businessLine || 'SOLAR')); }
  lines = LINES;
  enquiryRef = '';
  invoicesOn = INVOICES_ENABLED;
  get isInvoice() { return this.form.value.docType === 'PROFORMA' || this.form.value.docType === 'INVOICE'; }
  get docLabel() { return docTypeOf(this.form.value.docType).label; }
  get customerCode() { return this.customers.find(c => c.id === this.form.value.customerId)?.code ?? ''; }
  get line() { return lineOf(this.form.value.businessLine); }

  form = this.fb.nonNullable.group({
    businessLine: 'SOLAR',
    docType: 'QUOTATION',
    templateId: null as number | null,
    sourceId: null as number | null,
    poNo: '',
    dueDate: null as string | null,
    pdfTitle: '',
    showCover: true,
    showLetter: true,
    enquiryId: null as number | null,
    customerId: null as number | null,
    refNo: '',
    quoteDate: localDate(),
    clientName: ['', Validators.required],
    clientAddress: '', contactPerson: '', phone: ['', Validators.pattern(/^\d{0,10}$/)], projectLocation: '',
    capacityKw: [30, Validators.min(0)], gstPercent: [8.9, Validators.min(0)], showItemPrices: true,
    coverImageKey: '', signatoryName: '', signatoryTitle: '', signatoryPhone: '', signatoryEmail: '',
    items: this.fb.array<any>([]),
    sections: this.fb.array<any>([]),
  });

  get canSave() { return this.id ? this.auth.can('QUOTATIONS', 'EDIT') : this.auth.can('QUOTATIONS', 'CREATE'); }
  get items() { return this.form.controls.items as FormArray; }
  get sections() { return this.form.controls.sections as FormArray; }
  get subtotal() { return this.items.controls.reduce((s, c) => s + (Number(c.value.qty) || 0) * (Number(c.value.unitPrice) || 0), 0); }
  get gst() { return (this.subtotal * (Number(this.form.value.gstPercent) || 0)) / 100; }
  get total() { return this.subtotal + this.gst; }

  ngOnInit() {
    this.api.catalog().subscribe(c => { this.history = c; this.mergeCatalog(); });
    this.api.customers().subscribe({ next: c => (this.customers = c), error: () => (this.customers = []) });
    this.api.products().subscribe({ next: p => (this.products = p), error: () => (this.products = []) });
    // master parts first in the equipment suggestions, then items used in earlier statements
    this.api.parts().subscribe({ next: p => { this.masterParts = p; this.mergeCatalog(); }, error: () => (this.masterParts = []) });
    const p = this.route.snapshot.paramMap.get('id');
    if (!p) {
      this.api.list().subscribe(l => (this.previous = l));
      const enq = Number(this.route.snapshot.queryParamMap.get('enquiry'));
      if (enq) { this.fromEnquiry(enq); return; }
      // a new statement starts from the default template of its type and line
      this.loadTemplates();
      return;
    }
    this.id = +p;
    this.api.get(this.id).subscribe(q => {
      const { items, sections, ...rest } = q;
      this.form.patchValue(this.clean(rest));
      this.form.patchValue({ enquiryId: q.enquiryId ?? null, customerId: q.customerId ?? null });
      if (q.enquiryId) this.api.enquiry(q.enquiryId).subscribe({ next: d => (this.enquiryRef = d.enquiry.refNo ?? ''), error: () => (this.enquiryRef = '') });
      items.forEach(i => { this.items.push(this.newItem(i)); if (i.imageKey) this.loadPreview(i.imageKey); });
      this.setSections(sections);
    });
  }

  private setSections(list: QSection[]) {
    this.sections.clear();
    list.forEach(s => { this.sections.push(this.newSection(s)); (s.imageKeys ?? []).forEach(k => this.loadPreview(k)); });
  }

  newSection(s: Partial<QSection> = {}) {
    return new FormGroup({
      kind: new FormControl(s.kind ?? 'TEXT', { nonNullable: true }),
      title: new FormControl(s.title ?? '', { nonNullable: true }),
      content: new FormControl(s.content ?? '', { nonNullable: true }),
      style: new FormControl(s.style ?? 'BULLETS', { nonNullable: true }),
      visible: new FormControl(s.visible ?? true, { nonNullable: true }),
      imageColumns: new FormControl(s.imageColumns ?? 2, { nonNullable: true }),
      imageKeys: new FormControl<string[]>(s.imageKeys ?? [], { nonNullable: true }),
    });
  }

  addSection() { this.sections.push(this.newSection({ title: 'New section' })); }

  move(i: number, dir: number) {
    const c = this.sections.at(i);
    this.sections.removeAt(i);
    this.sections.insert(i + dir, c);
  }

  onSectionFiles(ev: Event, i: number) {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    files.forEach(f => this.api.upload(f).subscribe({
      next: r => {
        const c = this.sections.at(i).get('imageKeys')!;
        c.setValue([...c.value, r.key]);
        this.loadPreview(r.key);
      },
      error: () => (this.error = 'A photo could not be uploaded. Use JPG or PNG files under 20 MB.'),
    }));
  }

  removeImage(sectionIndex: number, imageIndex: number) {
    const c = this.sections.at(sectionIndex).get('imageKeys')!;
    c.setValue(c.value.filter((_: string, n: number) => n !== imageIndex));
  }

  /** Turns null values from the server into empty strings so the form fields stay valid. */
  private clean(o: any) {
    const r: any = {};
    for (const k of Object.keys(o)) r[k] = o[k] ?? (typeof o[k] === 'boolean' ? false : '');
    return r;
  }

  newItem(i: Partial<Item> = {}) {
    return this.fb.group({
      equipment: [i.equipment ?? '', Validators.required],
      specification: [i.specification ?? ''],
      make: [i.make ?? ''],
      hsn: [i.hsn ?? ''],
      uom: [i.uom ?? 'Nos.'],
      qty: [i.qty ?? 1],
      unitPrice: [i.unitPrice ?? 0],
      imageKey: [i.imageKey ?? (null as string | null)],
    });
  }

  addItem() { this.items.push(this.newItem()); }

  removeAll() {
    if (this.items.length && confirm(`Remove all ${this.items.length} item(s) from this statement?`)) this.items.clear();
  }

  /** Phone: keep digits only (no minus sign, spaces or letters), at most 10. */
  digitsOnly(ev: Event) {
    const el = ev.target as HTMLInputElement;
    const clean = el.value.replace(/\D/g, '').slice(0, 10);
    if (clean !== el.value) { el.value = clean; this.form.controls.phone.setValue(clean); }
  }

  /** Number boxes that must not be negative: ignore the minus, plus and exponent keys. */
  blockSign(ev: KeyboardEvent) { if (['-', '+', 'e', 'E'].includes(ev.key)) ev.preventDefault(); }

  /** Re-use an item from an earlier statement: fills only rows that are still empty. */
  fillFromCatalog(i: number) {
    const g = this.items.at(i);
    const name = (g.value.equipment || '').trim().toLowerCase();
    const e = this.catalog.find(x => x.equipment.trim().toLowerCase() === name);
    if (!e || g.value.specification || Number(g.value.unitPrice)) return;
    g.patchValue({ specification: e.specification ?? '', make: e.make ?? '', uom: e.uom || 'Nos.', unitPrice: e.unitPrice ?? 0, imageKey: e.imageKey ?? null });
    if (e.imageKey) this.loadPreview(e.imageKey);
  }

  private loadTemplates() {
    this.api.templates().subscribe({
      next: t => { this.templates = t; this.useTemplate(this.defaultTemplate()); },
      error: () => this.api.template(this.form.value.businessLine!).subscribe(s => this.setSections(s)),
    });
  }
  private defaultTemplate() { return this.lineTemplates.find(t => t.isDefault) ?? this.lineTemplates[0] ?? null; }

  /** Applies a template: its PDF options and a fresh copy of its sections. */
  private useTemplate(t: DocTemplate | null) {
    if (!t) { this.api.template(this.form.value.businessLine!).subscribe(s => this.setSections(s)); return; }
    this.form.patchValue({ templateId: t.id!, pdfTitle: t.pdfTitle ?? '', showCover: t.showCover, showLetter: t.showLetter, showItemPrices: t.showPrices });
    this.api.template(t.businessLine, t.docType, t.id).subscribe(s => this.setSections(s));
  }

  pickTemplate(id: number) {
    const t = this.templates.find(x => x.id === id);
    if (!t) return;
    if (this.sections.dirty && !confirm('Switch the template? The PDF sections are replaced with the sections of that template.')) return;
    this.useTemplate(t);
  }

  /** Proforma / tax invoice from this document (from its last saved version). */
  convert(type: string) {
    const label = docTypeOf(type).label.toLowerCase();
    if (this.form.dirty && !confirm(`Unsaved changes are not copied. Create the ${label} from the last saved version?`)) return;
    this.api.convert(this.id!, type).subscribe({
      next: q => this.router.navigate(['/quote', q.id]).then(() => location.reload()),
      error: e => (this.error = e.error?.message || `Could not create the ${label}`),
    });
  }

  /** New statement for an enquiry: its line, customer details and capacity, and the link back to it. */
  private fromEnquiry(id: number) {
    this.api.enquiry(id).subscribe({
      next: d => {
        const l = lineOf(d.enquiry.businessLine), c = d.customer;
        this.enquiryRef = d.enquiry.refNo ?? '';
        this.form.patchValue({
          enquiryId: d.enquiry.id!, customerId: d.enquiry.customerId, businessLine: l.key, gstPercent: l.gst, capacityKw: d.enquiry.capacity ?? l.capacity,
          clientName: c?.name ?? '', clientAddress: c?.address ?? '', contactPerson: c?.contactPerson ?? '',
          phone: (c?.phone ?? '').replace(/\D/g, '').slice(0, 10), projectLocation: c?.city ?? '',
        });
        this.loadTemplates();
      },
      error: () => this.loadTemplates(),
    });
  }

  /** New statement only: switching the line reloads its standard sections and the line's capacity and GST defaults. */
  setLine(key: string) {
    if (key === this.form.value.businessLine) return;
    const touched = this.sections.dirty || this.items.length > 0;
    if (touched && !confirm('Switch the business line? The PDF sections are replaced with the standard text of that line. Items and client details stay.')) return;
    const l = lineOf(key);
    this.form.patchValue({ businessLine: l.key, gstPercent: l.gst, capacityKw: l.capacity });
    this.useTemplate(this.defaultTemplate());
  }

  private history: CatalogEntry[] = [];
  private mergeCatalog() {
    const fromMaster: CatalogEntry[] = this.masterParts.filter(p => p.active).map(p => ({
      equipment: p.name, specification: p.specification, make: p.make, uom: p.uom, unitPrice: p.unitPrice, imageKey: p.imageKey }));
    const seen = new Set(fromMaster.map(e => e.equipment.trim().toLowerCase()));
    this.catalog = [...fromMaster, ...this.history.filter(e => !seen.has(e.equipment.trim().toLowerCase()))];
  }

  /** Parts master -> one item with the part's current details and price. */
  addPart(id: string) {
    const p = this.masterParts.find(x => String(x.id) === id);
    if (!p) return;
    this.items.push(this.newItem({ equipment: p.name, specification: p.specification ?? '', make: p.make ?? '', hsn: p.hsnCode ?? '', uom: p.uom || 'Nos.', qty: 1, unitPrice: Number(p.unitPrice) || 0, imageKey: p.imageKey }));
    if (p.imageKey) this.loadPreview(p.imageKey);
  }

  /** Product master -> every part becomes an item; an empty statement also takes the product's capacity. */
  addProduct(id: string) {
    const p = this.products.find(x => String(x.id) === id);
    if (!p) return;
    const wasEmpty = this.items.length === 0;
    p.parts.forEach(x => {
      this.items.push(this.newItem({ equipment: x.name, specification: x.specification ?? '', make: x.make ?? '', hsn: x.hsn ?? '', uom: x.uom || 'Nos.',
        qty: Number(x.qty) || 1, unitPrice: Number(x.unitPrice) || 0, imageKey: x.imageKey }));
      if (x.imageKey) this.loadPreview(x.imageKey);
    });
    if (wasEmpty && p.capacity != null) this.form.patchValue({ capacityKw: Number(p.capacity) });
  }

  /** Customer master -> client fields of this statement (the statement keeps its own copy). */
  pickCustomer(id: string) {
    const c = this.customers.find(x => String(x.id) === id);
    if (!c) return;
    this.form.patchValue({
      customerId: c.id ?? null, clientName: c.name, clientAddress: c.address ?? '', contactPerson: c.contactPerson ?? '',
      phone: (c.phone ?? '').replace(/\D/g, '').slice(0, 10), projectLocation: c.city ?? this.form.value.projectLocation ?? '',
    });
  }

  copyFrom(id: string) {
    if (!id) return;
    if (this.items.length > 0 && !confirm('Replace the items and text on this page with the previous statement?')) return;
    this.api.get(+id).subscribe(q => {
      this.form.patchValue(this.clean({
        businessLine: q.businessLine || 'SOLAR', gstPercent: q.gstPercent, capacityKw: q.capacityKw, showItemPrices: q.showItemPrices, coverImageKey: q.coverImageKey,
        signatoryName: q.signatoryName, signatoryTitle: q.signatoryTitle, signatoryPhone: q.signatoryPhone, signatoryEmail: q.signatoryEmail,
      }));
      this.setSections(q.sections);
      this.items.clear();
      q.items.forEach(i => { this.items.push(this.newItem(i)); if (i.imageKey) this.loadPreview(i.imageKey); });
    });
  }

  importPaste() {
    this.paste.split('\n').map(l => l.split('\t')).filter(c => c[0]?.trim()).forEach(c =>
      this.items.push(this.newItem({
        equipment: c[0].trim(), specification: c[1]?.trim() ?? '', make: c[2]?.trim() ?? '',
        uom: c[3]?.trim() || 'Nos.', qty: Number(c[4]) || 1, unitPrice: Number((c[5] ?? '').replace(/,/g, '')) || 0,
      })));
    this.paste = '';
  }

  onFile(ev: Event, index: number) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (!f) return;
    this.api.upload(f).subscribe({
      next: r => { this.items.at(index).patchValue({ imageKey: r.key }); this.loadPreview(r.key); },
      error: () => (this.error = 'That file could not be uploaded. Use a JPG or PNG under 20 MB.'),
    });
  }

  loadPreview(key: string) { this.api.image(key).subscribe(b => (this.previews[key] = URL.createObjectURL(b))); }

  save(downloadPdf: boolean) {
    this.error = '';
    for (let i = this.items.length - 1; i >= 0; i--) {   // drop blank rows
      if (!this.items.at(i).value.equipment?.trim()) this.items.removeAt(i);
    }
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error = this.form.controls.phone.invalid ? 'Phone must be digits only, up to 10 digits.' : this.form.controls.capacityKw.invalid ? 'Capacity cannot be negative.' : this.form.controls.gstPercent.invalid ? 'GST cannot be negative.' : 'Enter the client name.'; return; }
    const raw: any = this.form.getRawValue();
    const body: any = { ...raw, sections: raw.sections.map((x: any, n: number) => ({ ...x, position: n })), id: this.id };
    this.api.save(body).subscribe({
      next: q => { if (downloadPdf) this.api.download(q.id!, q.refNo); this.router.navigate(q.enquiryId ? ['/enquiries', q.enquiryId] : ['/statements']); },
      error: e => (this.error = (e.status === 403 || e.status === 400) && e.error?.message ? e.error.message : 'The statement could not be saved. Try again.'),
    });
  }
}
