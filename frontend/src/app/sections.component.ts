import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Api, Company, LINES, Section, lineOf } from './api';
import { AuthService } from './auth';
import { BrandService } from './brand';
import { ImagePickerComponent } from './image-picker.component';

@Component({
  standalone: true,
  imports: [FormsModule, ImagePickerComponent],
  template: `
    <div class="page-h"><div><h1>Settings</h1><p class="muted">Configure how the app behaves for your company.</p></div></div>
    <div class="tabs">
      <button [class.on]="tab === 'company'" (click)="tab = 'company'">Company</button>
      @if (auth.can('TEMPLATE_DATA', 'VIEW')) { <button [class.on]="tab === 'text'" (click)="tab = 'text'">Standard text</button> }
      <button [class.on]="tab === 'account'" (click)="tab = 'account'">Account</button>
    </div>

    @if (tab === 'company') {
      @if (!auth.can('TEMPLATES', 'EDIT')) { <p class="hint">You can view the company details but not change them.</p> }
      <fieldset class="plain" [disabled]="!auth.can('TEMPLATES', 'EDIT')">
      <div class="card">
        <div class="card-h"><div><h3>General</h3><p>Name, address and who signs the cover letter. The name and logo appear at the top of every PDF page.</p></div></div>
        <div class="grid">
          <label>Company name<input [(ngModel)]="c.companyName" /></label>
          <label class="wide">Company address<input [(ngModel)]="c.address" /></label>
          <label>Signatory name (for example your manager)<input [(ngModel)]="c.signatoryName" /></label>
          <label>Signatory designation<input [(ngModel)]="c.signatoryTitle" /></label>
          <label>Signatory phone<input [(ngModel)]="c.signatoryPhone" /></label>
          <label>Signatory email<input [(ngModel)]="c.signatoryEmail" /></label>
        </div>
      </div>
      <div class="card">
        <div class="card-h"><div><h3>Images</h3><p>Logo, default cover image and the system diagram used by new statements.</p></div></div>
        <div class="grid3">
          <image-picker label="Logo (top right of every page)" [key]="c.logoKey" (keyChange)="c.logoKey = $event" />
          <image-picker label="Default cover page image" [key]="c.coverImageKey" (keyChange)="c.coverImageKey = $event" />
          <image-picker label="System description diagram" [key]="c.diagramImageKey" (keyChange)="c.diagramImageKey = $event" />
        </div>
      </div>
      </fieldset>
      @if (auth.can('TEMPLATES', 'EDIT')) { <button (click)="saveCompany()">Save company details</button> }
      @if (companySaved) { <span class="ok">Saved</span> }
    }

    @if (tab === 'text' && auth.can('TEMPLATE_DATA', 'VIEW')) {
      <fieldset class="plain" [disabled]="!auth.can('TEMPLATE_DATA', 'EDIT')">
      <div class="card">
        <div class="seg" style="margin-bottom:14px">
          @for (l of lines; track l.key) { <button type="button" [class.on]="line === l.key" (click)="setLine(l.key)">{{ l.label }}</button> }
        </div>
        <div class="card-h"><div><h3>Standard text for new {{ lineLabel }} statements</h3>
          <p>New statements start with this text and you can change everything inside each statement. One point per line. For Terms &amp; Conditions write each row as <b>Topic | Details</b>. In the project brief you can use {{ '{capacity}' }}, {{ '{location}' }} and {{ '{client}' }}.</p></div></div>
        @for (s of sections; track s.id) {
          <div class="sec">
            <div class="row">
              <input class="grow" [(ngModel)]="s.title" />
              @if (s.sectionKey.startsWith('CUSTOM_') && auth.can('TEMPLATE_DATA', 'DELETE')) { <button type="button" class="link danger" (click)="remove(s)">Delete section</button> }
            </div>
            <textarea rows="7" [(ngModel)]="s.content"></textarea>
          </div>
        }
      </div>
      </fieldset>
      @if (auth.can('TEMPLATE_DATA', 'CREATE')) {
      <div class="card">
        <div class="card-h"><div><h3>Add a standard section</h3><p>It will be included in every new {{ lineLabel }} statement.</p></div></div>
        <div class="sec">
          <input placeholder="Section title" [(ngModel)]="newTitle" />
          <textarea rows="3" placeholder="Text (one point per line)" [(ngModel)]="newContent"></textarea>
          <div><button (click)="addSection()">Add section</button></div>
        </div>
      </div>
      }
      @if (auth.can('TEMPLATE_DATA', 'EDIT')) {
        <button (click)="save()">Save standard text</button>
        <button class="link danger" (click)="reset()">Restore built-in text</button>
      }
      @if (saved) { <span class="ok">Saved</span> }
    }

    @if (tab === 'account') {
      <div class="card">
        <div class="card-h"><div><h3>Sign-in details</h3><p>Signed in as <b>{{ email }}</b></p></div></div>
        @if (auth.isAdmin()) {
          <div class="grid">
            <label>New email<input type="email" [(ngModel)]="newEmail" /></label>
            <label>Current password<input type="password" [(ngModel)]="emailPassword" /></label>
            <div><button (click)="changeEmail()">Change email</button></div>
          </div>
          <hr />
        } @else { <p class="hint">Your sign-in email is managed by your administrator.</p> }
        <div class="grid">
          <label>Current password<input type="password" [(ngModel)]="oldPassword" /></label>
          <label>New password (min 8 characters)<input type="password" [(ngModel)]="newPassword" /></label>
          <div><button (click)="changePassword()">Change password</button></div>
        </div>
        @if (accountMsg) { <p [class.error]="accountError" [class.ok]="!accountError">{{ accountMsg }}</p> }
      </div>
    }
  `,
})
export class SectionsComponent implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private brand = inject(BrandService);
  auth = inject(AuthService);

  tab = 'company';
  sections: Section[] = [];
  c: Company = { companyName: '', address: '', logoKey: null, coverImageKey: null, diagramImageKey: null,
    signatoryName: '', signatoryTitle: '', signatoryPhone: '', signatoryEmail: '' };
  saved = false;
  companySaved = false;
  email = ''; newEmail = ''; emailPassword = ''; oldPassword = ''; newPassword = '';
  accountMsg = ''; accountError = false;
  newTitle = ''; newContent = '';
  lines = LINES;
  line = 'SOLAR';
  get lineLabel() { return lineOf(this.line).label; }

  ngOnInit() {
    this.route.paramMap.subscribe(p => (this.tab = p.get('tab') || 'company'));
    this.api.account().subscribe(a => (this.email = a.email));
    if (this.auth.can('TEMPLATE_DATA', 'VIEW')) this.loadSections();
    this.api.company().subscribe(c => (this.c = { ...this.c, ...Object.fromEntries(Object.entries(c).map(([k, v]) => [k, v ?? (k.endsWith('Key') ? null : '')])) } as Company));
  }

  loadSections() { this.api.sections(this.line).subscribe(s => (this.sections = s)); }
  setLine(key: string) { if (key !== this.line) { this.line = key; this.saved = false; this.loadSections(); } }

  private say(msg: string, error = false) { this.accountMsg = msg; this.accountError = error; }
  private fail = (e: any) => this.say(e.error?.message || 'Something went wrong. Try again.', true);

  changeEmail() {
    this.api.changeEmail(this.newEmail, this.emailPassword).subscribe({
      next: r => { this.email = r.email; this.brand.email.set(r.email); this.newEmail = ''; this.emailPassword = ''; this.say('Email changed. Use it the next time you sign in.'); },
      error: this.fail,
    });
  }

  changePassword() {
    this.api.changePassword(this.oldPassword, this.newPassword).subscribe({
      next: () => { this.oldPassword = ''; this.newPassword = ''; this.say('Password changed.'); },
      error: this.fail,
    });
  }

  saveCompany() {
    this.api.saveCompany(this.c).subscribe(() => {
      this.brand.refresh();   // refresh the name and logo in the top bar
      this.companySaved = true;
      setTimeout(() => (this.companySaved = false), 2500);
    });
  }

  addSection() {
    if (!this.newTitle.trim()) return;
    this.api.addSection(this.line, this.newTitle, this.newContent).subscribe(s => { this.sections.push(s); this.newTitle = ''; this.newContent = ''; });
  }

  remove(s: Section) {
    if (confirm(`Delete the standard section "${s.title}"? Existing statements keep their own copy.`))
      this.api.deleteSection(s.id).subscribe(() => (this.sections = this.sections.filter(x => x.id !== s.id)));
  }

  save() {
    this.api.saveSections(this.line, this.sections).subscribe(s => { this.sections = s; this.saved = true; setTimeout(() => (this.saved = false), 2500); });
  }

  reset() {
    if (confirm(`Replace all ${this.lineLabel} standard text with the built-in version? Your edits to it will be lost.`))
      this.api.resetSections(this.line).subscribe(s => (this.sections = s));
  }
}
