import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth';
import { BrandService } from './brand';
import { IconComponent } from './icon.component';

/**
 * Data tables get class "data" (one line per row, scroll sideways). Item editors (table.items) instead become labelled
 * cards on phones: every cell gets its column header as data-label (styles.css, "small screens").
 */
function labelTables() {
  document.querySelectorAll<HTMLTableElement>('main table:not(.matrix):not(.items), .modal table:not(.matrix):not(.items)').forEach(t => t.classList.add('data'));
  document.querySelectorAll<HTMLTableElement>('main table.items, .modal table.items').forEach(t => {
    const heads = Array.from(t.querySelectorAll('thead th'), th => th.textContent?.trim() ?? '');
    t.classList.add('cards');
    t.querySelectorAll('tbody tr').forEach(tr =>
      Array.from(tr.children).forEach((td, i) => { if (td.getAttribute('data-label') !== heads[i]) td.setAttribute('data-label', heads[i] ?? ''); }));
  });
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    @if (!auth.loggedIn || auth.mustChange()) {
      <router-outlet />
    } @else {
      <div class="shell" [class.collapsed]="collapsed">
        <div class="side-backdrop" (click)="collapsed = true"></div>
        <aside class="side">
          <div class="brand"><span class="mark">@if (brand.logo()) { <img [src]="brand.logo()" alt="" /> } @else { {{ brandInitials }} }</span><span class="brand-name">{{ brand.name() || 'Quotations' }}</span></div>
          <nav class="nav">
            @if (auth.isAdmin()) {
              <div class="group">Administration</div>
              <a routerLink="/admin" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }"><app-icon name="shield" /><span>Overview</span></a>
              <a routerLink="/admin/users" routerLinkActive="on"><app-icon name="users" /><span>Users</span></a>
              <a routerLink="/admin/access" routerLinkActive="on"><app-icon name="unlock" /><span>Grant access</span></a>
              <a routerLink="/admin/roles" routerLinkActive="on"><app-icon name="key" /><span>Roles &amp; rights</span></a>
              <a routerLink="/admin/activity" routerLinkActive="on"><app-icon name="activity" /><span>Activity log</span></a>
              <div class="group">Workspace</div>
            }
            <a routerLink="/" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }"><app-icon name="dashboard" /><span>Dashboard</span></a>
            @if (auth.can('CUSTOMERS', 'VIEW')) { <a routerLink="/enquiries" routerLinkActive="on"><app-icon name="funnel" /><span>Enquiries</span></a> }
            @if (auth.can('QUOTATIONS', 'VIEW')) { <a routerLink="/statements" routerLinkActive="on"><app-icon name="file" /><span>Statements</span></a> }
            @if (auth.can('QUOTATIONS', 'CREATE')) { <a routerLink="/quote/new" routerLinkActive="on"><app-icon name="plus" /><span>New statement</span></a> }
            @if (auth.can('CUSTOMERS', 'VIEW') || auth.can('PRODUCTS', 'VIEW')) { <div class="group">Masters</div> }
            @if (auth.can('PRODUCTS', 'VIEW')) {
              <a routerLink="/products" routerLinkActive="on"><app-icon name="box" /><span>Products</span></a>
              <a routerLink="/parts" routerLinkActive="on"><app-icon name="cog" /><span>Parts</span></a>
            }
            @if (auth.can('CUSTOMERS', 'VIEW')) { <a routerLink="/customers" routerLinkActive="on"><app-icon name="contact" /><span>Customers</span></a> }
            <a routerLink="/settings" routerLinkActive="on"><app-icon name="settings" /><span>Settings</span></a>
          </nav>
        </aside>

        <div class="main">
          <header class="top">
            <button class="icon-btn" (click)="collapsed = !collapsed" aria-label="Toggle menu"><app-icon name="menu" /></button>
            <div class="company">
              @if (brand.logo()) { <img [src]="brand.logo()" alt="Company logo" /> }
              <strong>{{ brand.name() }}</strong>
              <span class="divider"></span>
              <span class="page">{{ title }}</span>
            </div>
            <span class="spacer"></span>
            <button class="icon-btn" (click)="toggleTheme()" aria-label="Toggle dark mode"><app-icon [name]="dark ? 'sun' : 'moon'" /></button>
            <div class="usermenu">
              <button class="user" (click)="menuOpen = !menuOpen">
                <span class="avatar">{{ initials }}</span>
                <span class="uname">{{ auth.userName() || brand.name() }}</span>
                <app-icon name="chevron" [size]="16" />
              </button>
              @if (menuOpen) {
                <div class="backdrop" (click)="menuOpen = false"></div>
                <div class="dropdown">
                  <div class="who"><b>{{ auth.userName() || brand.name() }}</b><small>{{ brand.email() }}</small><small>{{ auth.isAdmin() ? 'Administrator' : 'Employee' }}</small></div>
                  <a routerLink="/settings"><app-icon name="settings" [size]="18" /> Settings</a>
                  <button class="out" (click)="auth.logout()"><app-icon name="logout" [size]="18" /> Sign out</button>
                </div>
              }
            </div>
          </header>
          <main><router-outlet /></main>
          <footer>&copy; {{ year }} {{ brand.name() }}. All rights reserved.</footer>
        </div>
      </div>
    }
  `,
})
export class AppComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  brand = inject(BrandService);
  private router = inject(Router);
  collapsed = window.innerWidth < 860;
  menuOpen = false;
  dark = localStorage.getItem('theme') === 'dark';
  year = new Date().getFullYear();

  /** On phones tables are shown as cards: every cell gets its column header as data-label (styles.css, "small screens"). */
  private labeller = new MutationObserver(() => requestAnimationFrame(() => labelTables()));

  constructor() { this.applyTheme(); this.labeller.observe(document.body, { childList: true, subtree: true, characterData: true }); }

  ngOnDestroy() { this.labeller.disconnect(); }

  ngOnInit() {
    if (this.auth.loggedIn && !this.auth.mustChange()) {
      this.brand.refresh();
      this.auth.me().subscribe();   // pick up rights the administrator changed since the last visit
    }
    this.router.events.subscribe(e => {
      if (e instanceof NavigationEnd) { this.menuOpen = false; if (window.innerWidth < 860) this.collapsed = true; }
    });
  }

  get title() {
    const u = this.router.url;
    if (u === '/' || u === '') return 'Dashboard';
    if (u.startsWith('/admin/users')) return 'Users';
    if (u.startsWith('/admin/access')) return 'Grant access';
    if (u.startsWith('/admin/roles')) return 'Roles & rights';
    if (u.startsWith('/admin/activity')) return 'Activity log';
    if (u.startsWith('/admin')) return 'Administration';
    if (u.startsWith('/statements')) return 'Statements';
    if (u.startsWith('/quote')) return 'Statement';
    if (u.startsWith('/customers')) return 'Customers';
    if (u.startsWith('/enquiries')) return 'Enquiries';
    if (u.startsWith('/products')) return 'Products';
    if (u.startsWith('/parts')) return 'Parts';
    if (u.startsWith('/settings')) return 'Settings';
    return '';
  }

  get brandInitials() { return (this.brand.name() || 'Q').split(/\s+/).filter(w => /[A-Za-z]/.test(w[0])).map(w => w[0]).join('').slice(0, 2).toUpperCase(); }

  get initials() {
    const n = this.auth.userName() || this.brand.name() || this.brand.email() || '?';
    return n.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  }

  toggleTheme() {
    this.dark = !this.dark;
    localStorage.setItem('theme', this.dark ? 'dark' : 'light');
    this.applyTheme();
  }

  private applyTheme() { document.documentElement.setAttribute('data-theme', this.dark ? 'dark' : 'light'); }
}
